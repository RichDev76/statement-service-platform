package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.service.SignedLinkService;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@DisplayName("StatementEntityMapper Tests")
class StatementEntityMapperTest {

    @Autowired
    private StatementEntityMapper statementEntityMapper;

    @MockBean
    private SignedLinkService signedLinkService;

    private URI testDownloadLink;

    @BeforeEach
    void setUp() {
        testDownloadLink = URI.create("https://example.com/download/test.pdf?expires=123&signature=abc");
    }

    @Test
    @DisplayName("toDto - should map all fields from entity to DTO with download link")
    void toDto_AllFields() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDate statementDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime uploadedAt = OffsetDateTime.now();

        Statement entity = new Statement();
        entity.setId(id);
        entity.setAccountNumber("ACC123456");
        entity.setStatementDate(statementDate);
        entity.setUploadFileName("statement.pdf");
        entity.setSizeBytes(2048L);
        entity.setUploadedAt(uploadedAt);
        entity.setUploadedBy("testUser");
        entity.setEncrypted(true);
        entity.setContentHash("abc123");
        entity.setFilePath("/path/to/file.pdf.enc");

        when(signedLinkService.buildSignedLink("statement.pdf", id)).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("ACC123456");
        assertThat(result.getStatementDate()).isEqualTo(statementDate);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.getDownloadLink()).isEqualTo(testDownloadLink);

        verify(signedLinkService).buildSignedLink("statement.pdf", id);
    }

    @Test
    @DisplayName("toDto - should handle null entity")
    void toDto_NullEntity() {
        // When
        StatementDto result = statementEntityMapper.toDto(null, signedLinkService);

        // Then
        assertThat(result).isNull();
        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDto - should generate download link using SignedLinkService")
    void toDto_GeneratesDownloadLink() {
        // Given
        UUID id = UUID.randomUUID();
        String fileName = "report.pdf";

        Statement entity = new Statement();
        entity.setId(id);
        entity.setUploadFileName(fileName);
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(fileName, id)).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result.getDownloadLink()).isEqualTo(testDownloadLink);
        verify(signedLinkService).buildSignedLink(fileName, id);
    }

    @Test
    @DisplayName("toDto - should map id to statementId")
    void toDto_IdMapping() {
        // Given
        UUID id = UUID.randomUUID();

        Statement entity = new Statement();
        entity.setId(id);
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result.getStatementId()).isEqualTo(id);
    }

    @Test
    @DisplayName("toDto - should map uploadFileName to fileName")
    void toDto_FileNameMapping() {
        // Given
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("original-name.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result.getFileName()).isEqualTo("original-name.pdf");
    }

    @Test
    @DisplayName("toDto - should map sizeBytes to fileSize")
    void toDto_FileSizeMapping() {
        // Given
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(4096L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result.getFileSize()).isEqualTo(4096L);
    }

    @Test
    @DisplayName("toDtoWithoutLink - should map fields without generating download link")
    void toDtoWithoutLink_NoLink() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDate statementDate = LocalDate.of(2024, 6, 20);

        Statement entity = new Statement();
        entity.setId(id);
        entity.setAccountNumber("ACC999");
        entity.setStatementDate(statementDate);
        entity.setUploadFileName("statement.pdf");
        entity.setSizeBytes(2048L);
        entity.setUploadedAt(OffsetDateTime.now());

        // When
        StatementDto result = statementEntityMapper.toDtoWithoutLink(entity, signedLinkService);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("ACC999");
        assertThat(result.getStatementDate()).isEqualTo(statementDate);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getDownloadLink()).isNull();

        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDtoWithoutLink - should handle null entity")
    void toDtoWithoutLink_NullEntity() {
        // When
        StatementDto result = statementEntityMapper.toDtoWithoutLink(null, signedLinkService);

        // Then
        assertThat(result).isNull();
        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDtoWithoutLink - should map all fields except downloadLink")
    void toDtoWithoutLink_AllFieldsExceptLink() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 3, 10);
        OffsetDateTime uploadedAt = OffsetDateTime.now();

        Statement entity = new Statement();
        entity.setId(id);
        entity.setAccountNumber("ACC555");
        entity.setStatementDate(date);
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(8192L);
        entity.setUploadedAt(uploadedAt);

        // When
        StatementDto result = statementEntityMapper.toDtoWithoutLink(entity, signedLinkService);

        // Then
        assertThat(result.getStatementId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("ACC555");
        assertThat(result.getStatementDate()).isEqualTo(date);
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getFileSize()).isEqualTo(8192L);
        assertThat(result.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toDtos - should map list of entities using withoutLink variant")
    void toDtos_MultipleEntities() {
        // Given
        Statement entity1 = createStatement("ACC001", "file1.pdf", 1024L);
        Statement entity2 = createStatement("ACC002", "file2.pdf", 2048L);
        Statement entity3 = createStatement("ACC003", "file3.pdf", 4096L);

        List<Statement> entities = Arrays.asList(entity1, entity2, entity3);

        // When
        List<StatementDto> result = statementEntityMapper.toDtos(entities, signedLinkService);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC001");
        assertThat(result.get(0).getFileName()).isEqualTo("file1.pdf");
        assertThat(result.get(0).getDownloadLink()).isNull();
        assertThat(result.get(1).getAccountNumber()).isEqualTo("ACC002");
        assertThat(result.get(1).getFileName()).isEqualTo("file2.pdf");
        assertThat(result.get(1).getDownloadLink()).isNull();
        assertThat(result.get(2).getAccountNumber()).isEqualTo("ACC003");
        assertThat(result.get(2).getFileName()).isEqualTo("file3.pdf");
        assertThat(result.get(2).getDownloadLink()).isNull();

        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDtos - should handle null list")
    void toDtos_NullList() {
        // When
        List<StatementDto> result = statementEntityMapper.toDtos(null, signedLinkService);

        // Then
        assertThat(result).isNull();
        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDtos - should handle empty list")
    void toDtos_EmptyList() {
        // Given
        List<Statement> emptyList = Collections.emptyList();

        // When
        List<StatementDto> result = statementEntityMapper.toDtos(emptyList, signedLinkService);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDtos - should handle single item list")
    void toDtos_SingleItem() {
        // Given
        Statement entity = createStatement("ACC123", "single.pdf", 1024L);
        List<Statement> entities = Collections.singletonList(entity);

        // When
        List<StatementDto> result = statementEntityMapper.toDtos(entities, signedLinkService);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC123");
        assertThat(result.get(0).getFileName()).isEqualTo("single.pdf");
        assertThat(result.get(0).getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toDtos - should preserve all properties in mapped DTOs")
    void toDtos_PreservesAllProperties() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 5, 15);
        OffsetDateTime uploadedAt = OffsetDateTime.now();

        Statement entity = new Statement();
        entity.setId(id);
        entity.setAccountNumber("ACC777");
        entity.setStatementDate(date);
        entity.setUploadFileName("full.pdf");
        entity.setSizeBytes(16384L);
        entity.setUploadedAt(uploadedAt);

        List<Statement> entities = Collections.singletonList(entity);

        // When
        List<StatementDto> result = statementEntityMapper.toDtos(entities, signedLinkService);

        // Then
        assertThat(result).hasSize(1);
        StatementDto dto = result.get(0);
        assertThat(dto.getStatementId()).isEqualTo(id);
        assertThat(dto.getAccountNumber()).isEqualTo("ACC777");
        assertThat(dto.getStatementDate()).isEqualTo(date);
        assertThat(dto.getFileName()).isEqualTo("full.pdf");
        assertThat(dto.getFileSize()).isEqualTo(16384L);
        assertThat(dto.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(dto.getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toDtos - should handle large list")
    void toDtos_LargeList() {
        // Given
        List<Statement> entities = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            entities.add(createStatement("ACC" + i, "file" + i + ".pdf", 1024L * i));
        }

        // When
        List<StatementDto> result = statementEntityMapper.toDtos(entities, signedLinkService);

        // Then
        assertThat(result).hasSize(100);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC0");
        assertThat(result.get(99).getAccountNumber()).isEqualTo("ACC99");
        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDto - should handle entity with null optional fields")
    void toDto_NullOptionalFields() {
        // Given
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());
        // accountNumber, statementDate, uploadedBy are null

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getAccountNumber()).isNull();
        assertThat(result.getStatementDate()).isNull();
    }

    @Test
    @DisplayName("toDto - should handle zero file size")
    void toDto_ZeroFileSize() {
        // Given
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("empty.pdf");
        entity.setSizeBytes(0L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toDto - should handle special characters in file name")
    void toDto_SpecialCharactersInFileName() {
        // Given
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("statement (2024) [final].pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(eq("statement (2024) [final].pdf"), any()))
                .thenReturn(testDownloadLink);

        // When
        StatementDto result = statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        assertThat(result.getFileName()).isEqualTo("statement (2024) [final].pdf");
        verify(signedLinkService).buildSignedLink("statement (2024) [final].pdf", entity.getId());
    }

    @Test
    @DisplayName("toDtoWithoutLink - should handle zero file size")
    void toDtoWithoutLink_ZeroFileSize() {
        // Given
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("empty.pdf");
        entity.setSizeBytes(0L);
        entity.setUploadedAt(OffsetDateTime.now());

        // When
        StatementDto result = statementEntityMapper.toDtoWithoutLink(entity, signedLinkService);

        // Then
        assertThat(result.getFileSize()).isEqualTo(0L);
        assertThat(result.getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toDtos - should not generate any links for any entity")
    void toDtos_NoLinksGenerated() {
        // Given
        List<Statement> entities = Arrays.asList(
                createStatement("ACC1", "file1.pdf", 1024L),
                createStatement("ACC2", "file2.pdf", 2048L),
                createStatement("ACC3", "file3.pdf", 4096L));

        // When
        List<StatementDto> result = statementEntityMapper.toDtos(entities, signedLinkService);

        // Then
        assertThat(result).allMatch(dto -> dto.getDownloadLink() == null);
        verifyNoInteractions(signedLinkService);
    }

    @Test
    @DisplayName("toDto - should call signedLinkService exactly once")
    void toDto_CallsSignedLinkServiceOnce() {
        // Given
        UUID id = UUID.randomUUID();
        Statement entity = new Statement();
        entity.setId(id);
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        statementEntityMapper.toDto(entity, signedLinkService);

        // Then
        verify(signedLinkService, times(1)).buildSignedLink("test.pdf", id);
    }

    @Test
    @DisplayName("toDto - should handle different date values")
    void toDto_DifferentDates() {
        // Given
        Statement entity1 = createStatement("ACC1", "file1.pdf", 1024L);
        entity1.setStatementDate(LocalDate.of(2020, 1, 1));

        Statement entity2 = createStatement("ACC2", "file2.pdf", 2048L);
        entity2.setStatementDate(LocalDate.of(2024, 12, 31));

        when(signedLinkService.buildSignedLink(any(), any())).thenReturn(testDownloadLink);

        // When
        StatementDto result1 = statementEntityMapper.toDto(entity1, signedLinkService);
        StatementDto result2 = statementEntityMapper.toDto(entity2, signedLinkService);

        // Then
        assertThat(result1.getStatementDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(result2.getStatementDate()).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    // Helper method
    private Statement createStatement(String accountNumber, String fileName, Long fileSize) {
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setAccountNumber(accountNumber);
        entity.setStatementDate(LocalDate.of(2024, 1, 15));
        entity.setUploadFileName(fileName);
        entity.setSizeBytes(fileSize);
        entity.setUploadedAt(OffsetDateTime.now());
        entity.setUploadedBy("testUser");
        entity.setEncrypted(true);
        entity.setContentHash("hash123");
        entity.setFilePath("/path/to/file.pdf.enc");
        return entity;
    }
}
