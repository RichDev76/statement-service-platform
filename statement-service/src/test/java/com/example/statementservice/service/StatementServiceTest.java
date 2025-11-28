package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.exception.StatementUploadException;
import com.example.statementservice.mapper.StatementEntityMapper;
import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.dto.UploadResponseDto;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.StatementRepository;
import java.io.File;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementService Unit Tests")
class StatementServiceTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private StatementEntityMapper statementEntityMapper;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private StatementService statementService;

    private Statement testStatement;
    private StatementDto testStatementDto;
    private UUID testId;
    private String testAccountNumber;
    private LocalDate testStatementDate;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testAccountNumber = "ACC123456";
        testStatementDate = LocalDate.of(2024, 1, 1);

        testStatement = new Statement();
        testStatement.setId(testId);
        testStatement.setAccountNumber(testAccountNumber);
        testStatement.setStatementDate(testStatementDate);
        testStatement.setUploadFileName("statement.pdf");
        testStatement.setFilePath("/path/to/statement.pdf");
        testStatement.setSizeBytes(1024L);
        testStatement.setUploadedAt(OffsetDateTime.now());
        testStatement.setUploadedBy("admin");
        testStatement.setEncrypted(true);
        testStatement.setContentHash("abc123");

        testStatementDto = new StatementDto();
        testStatementDto.setStatementId(testId);
        testStatementDto.setAccountNumber(testAccountNumber);
        testStatementDto.setStatementDate(testStatementDate);
    }

    @Test
    @DisplayName("uploadStatement - should successfully upload and persist statement")
    void uploadStatement_Success() {
        // Given
        String uploadedBy = "testUser";
        File mockFile = new File("/test/path/file.pdf");
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        String mockHash = "sha256hash";

        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);

        FileStorageService.FileStorageResult storageResult = new FileStorageService.FileStorageResult(mockFile, mockIv);
        when(fileStorageService.storeEncrypted(
                        any(UUID.class), eq(multipartFile), eq(testAccountNumber), eq(testStatementDate)))
                .thenReturn(storageResult);

        when(encryptionService.computeSha256Hex(multipartFile)).thenReturn(mockHash);
        when(statementRepository.saveAndFlush(any(Statement.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UploadResponseDto result =
                statementService.uploadStatement(testAccountNumber, testStatementDate, multipartFile, uploadedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getUploadedAt()).isNotNull();

        verify(fileStorageService)
                .storeEncrypted(any(UUID.class), eq(multipartFile), eq(testAccountNumber), eq(testStatementDate));
        verify(encryptionService).computeSha256Hex(multipartFile);
        verify(statementRepository).saveAndFlush(any(Statement.class));
    }

    @Test
    @DisplayName("uploadStatement - should use default 'admin' when uploadedBy is null")
    void uploadStatement_NullUploadedBy() {
        // Given
        File mockFile = new File("/test/path/file.pdf");
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        String mockHash = "sha256hash";

        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);

        FileStorageService.FileStorageResult storageResult = new FileStorageService.FileStorageResult(mockFile, mockIv);
        when(fileStorageService.storeEncrypted(any(), any(), any(), any())).thenReturn(storageResult);
        when(encryptionService.computeSha256Hex(any())).thenReturn(mockHash);
        when(statementRepository.saveAndFlush(any(Statement.class))).thenAnswer(i -> {
            Statement stmt = i.getArgument(0);
            assertThat(stmt.getUploadedBy()).isEqualTo("admin");
            return stmt;
        });

        // When
        statementService.uploadStatement(testAccountNumber, testStatementDate, multipartFile, null);

        // Then
        verify(statementRepository).saveAndFlush(any(Statement.class));
    }

    @Test
    @DisplayName("uploadStatement - should throw StatementUploadException on repository failure")
    void uploadStatement_RepositoryFailure() {
        // Given
        File mockFile = new File("/test/path/file.pdf");
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        String mockHash = "sha256hash";

        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);

        FileStorageService.FileStorageResult storageResult = new FileStorageService.FileStorageResult(mockFile, mockIv);
        when(fileStorageService.storeEncrypted(any(), any(), any(), any())).thenReturn(storageResult);
        when(encryptionService.computeSha256Hex(any())).thenReturn(mockHash);
        when(statementRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB error"));

        // When/Then
        assertThatThrownBy(() ->
                        statementService.uploadStatement(testAccountNumber, testStatementDate, multipartFile, "user"))
                .isInstanceOf(StatementUploadException.class)
                .hasMessageContaining("Failed to persist statement metadata");
    }

    @Test
    @DisplayName("getStatementById - should return statement when found")
    void getStatementById_Found() {
        // Given
        when(statementRepository.findStatementById(testId)).thenReturn(Optional.of(testStatement));

        // When
        Statement result = statementService.getStatementById(testId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testId);
        assertThat(result.getAccountNumber()).isEqualTo(testAccountNumber);
        verify(statementRepository).findStatementById(testId);
    }

    @Test
    @DisplayName("getStatementById - should throw StatementNotFoundException when not found")
    void getStatementById_NotFound() {
        // Given
        when(statementRepository.findStatementById(testId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> statementService.getStatementById(testId))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement not found for id: " + testId);
        verify(statementRepository).findStatementById(testId);
    }

    @Test
    @DisplayName("getStatementsByAccountNumber (pageable) - should return page of statements")
    void getStatementsByAccountNumber_Pageable() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementRepository.findByAccountNumber(testAccountNumber, pageable))
                .thenReturn(page);

        // When
        Page<Statement> result = statementService.getStatementsByAccountNumber(testAccountNumber, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo(testAccountNumber);
        verify(statementRepository).findByAccountNumber(testAccountNumber, pageable);
    }

    @Test
    @DisplayName("getStatementsByAccountNumber (list) - should return list of statements")
    void getStatementsByAccountNumber_List() {
        // Given
        List<Statement> statements = Arrays.asList(testStatement);
        when(statementRepository.findAllByAccountNumber(testAccountNumber)).thenReturn(Optional.of(statements));

        // When
        List<Statement> result = statementService.getStatementsByAccountNumber(testAccountNumber);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo(testAccountNumber);
        verify(statementRepository).findAllByAccountNumber(testAccountNumber);
    }

    @Test
    @DisplayName("getStatementsByAccountNumber (list) - should throw exception when not found")
    void getStatementsByAccountNumber_NotFound() {
        // Given
        when(statementRepository.findAllByAccountNumber(testAccountNumber)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> statementService.getStatementsByAccountNumber(testAccountNumber))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement(s) not found for account number: " + testAccountNumber);
        verify(statementRepository).findAllByAccountNumber(testAccountNumber);
    }

    @Test
    @DisplayName("getStatementByAccountNumberAndStatementDate - should return statement when found")
    void getStatementByAccountNumberAndStatementDate_Found() {
        // Given
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.of(testStatement));

        // When
        Optional<Statement> result =
                statementService.getStatementByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAccountNumber()).isEqualTo(testAccountNumber);
        assertThat(result.get().getStatementDate()).isEqualTo(testStatementDate);
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
    }

    @Test
    @DisplayName("getStatementByAccountNumberAndStatementDate - should return empty when not found")
    void getStatementByAccountNumberAndStatementDate_NotFound() {
        // Given
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.empty());

        // When
        Optional<Statement> result =
                statementService.getStatementByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);

        // Then
        assertThat(result).isEmpty();
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
    }

    @Test
    @DisplayName("toDto - should convert statement to DTO with link")
    void toDto_Success() {
        // Given
        when(statementEntityMapper.toDto(testStatement, signedLinkService)).thenReturn(testStatementDto);

        // When
        StatementDto result = statementService.toDto(testStatement);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(testId);
        verify(statementEntityMapper).toDto(testStatement, signedLinkService);
    }

    @Test
    @DisplayName("toDtoWithoutLink - should convert statement to DTO without link")
    void toDtoWithoutLink_Success() {
        // Given
        when(statementEntityMapper.toDtoWithoutLink(testStatement, signedLinkService))
                .thenReturn(testStatementDto);

        // When
        StatementDto result = statementService.toDtoWithoutLink(testStatement);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(testId);
        verify(statementEntityMapper).toDtoWithoutLink(testStatement, signedLinkService);
    }

    @Test
    @DisplayName("getStatementDtoById - should return statement DTO by ID")
    void getStatementDtoById_Success() {
        // Given
        when(statementRepository.findStatementById(testId)).thenReturn(Optional.of(testStatement));
        when(statementEntityMapper.toDto(testStatement, signedLinkService)).thenReturn(testStatementDto);

        // When
        StatementDto result = statementService.getStatementDtoById(testId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(testId);
        verify(statementRepository).findStatementById(testId);
        verify(statementEntityMapper).toDto(testStatement, signedLinkService);
    }

    @Test
    @DisplayName("getStatementsDtoByAccountNumber - should return list of statement DTOs")
    void getStatementsDtoByAccountNumber_Success() {
        // Given
        List<Statement> statements = Arrays.asList(testStatement);
        List<StatementDto> dtos = Arrays.asList(testStatementDto);

        when(statementRepository.findAllByAccountNumber(testAccountNumber)).thenReturn(Optional.of(statements));
        when(statementEntityMapper.toDtos(statements, signedLinkService)).thenReturn(dtos);

        // When
        List<StatementDto> result = statementService.getStatementsDtoByAccountNumber(testAccountNumber);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatementId()).isEqualTo(testId);
        verify(statementRepository).findAllByAccountNumber(testAccountNumber);
        verify(statementEntityMapper).toDtos(statements, signedLinkService);
    }

    @Test
    @DisplayName("getStatementDtoByAccountNumberAndStatementDate - should return DTO when found")
    void getStatementDtoByAccountNumberAndStatementDate_Found() {
        // Given
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.of(testStatement));
        when(statementEntityMapper.toDto(testStatement, signedLinkService)).thenReturn(testStatementDto);

        // When
        Optional<StatementDto> result =
                statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStatementId()).isEqualTo(testId);
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        verify(statementEntityMapper).toDto(testStatement, signedLinkService);
    }

    @Test
    @DisplayName("getStatementDtoByAccountNumberAndStatementDate - should return empty when not found")
    void getStatementDtoByAccountNumberAndStatementDate_NotFound() {
        // Given
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.empty());

        // When
        Optional<StatementDto> result =
                statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);

        // Then
        assertThat(result).isEmpty();
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        verify(statementEntityMapper, never()).toDto(any(), any());
    }
}
