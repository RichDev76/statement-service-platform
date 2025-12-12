package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.statementservice.enums.AuditAction;
import com.example.statementservice.enums.DownloadOutcome;
import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.StatementRepository;
import com.example.statementservice.service.DownloadService.DownloadStreamResult;
import com.example.statementservice.util.LinkValidationResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadService Unit Tests")
class DownloadServiceTest {

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DownloadService downloadService;

    @TempDir
    Path tempDir;

    private SignedLink testLink;
    private Statement testStatement;
    private String testToken;
    private String testClientIp;
    private String testUserAgent;
    private String testPerformedBy;
    private UUID testStatementId;
    private UUID testLinkId;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testLinkId = UUID.randomUUID();
        testToken = "test-token-1234567890";
        testClientIp = "192.168.1.1";
        testUserAgent = "Mozilla/5.0";
        testPerformedBy = "testUser";

        testLink = new SignedLink();
        testLink.setId(testLinkId);
        testLink.setStatementId(testStatementId);
        testLink.setToken(testToken);
        testLink.setCreatedAt(OffsetDateTime.now());
        testLink.setExpiresAt(OffsetDateTime.now().plusHours(1));
        testLink.setUsed(false);

        testStatement = new Statement();
        testStatement.setId(testStatementId);
        testStatement.setAccountNumber("ACC123456");
        testStatement.setStatementDate(LocalDate.of(2024, 1, 1));
        testStatement.setUploadFileName("statement.pdf");
        testStatement.setFilePath("/test/path/statement.pdf");
        testStatement.setSizeBytes(1024L);
        testStatement.setEncrypted(true);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should successfully download when all validations pass")
    void validateAndStreamDetailed_Success() throws Exception {
        // Given
        File tempFile = tempDir.resolve("test-statement.pdf.enc").toFile();
        Files.write(tempFile.toPath(), "encrypted content".getBytes());
        testStatement.setFilePath(tempFile.getAbsolutePath());

        LinkValidationResult validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        InputStream mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(encryptionService.decryptFileToStream(any(File.class))).thenReturn(mockStream);

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(result.stream()).isPresent();
        assertThat(result.stream().get()).isEqualTo(mockStream);

        verify(signedLinkService).validateAndConsume(testToken);
        verify(statementRepository).findById(testStatementId);
        verify(encryptionService).decryptFileToStream(any(File.class));
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_SUCCESS.getValue()),
                        eq(testStatementId),
                        eq("ACC123456"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName(
            "validateAndStreamDetailed - should return INVALID_SIGNATURE when link validation fails with NOT_FOUND")
    void validateAndStreamDetailed_InvalidSignature() {
        // Given
        LinkValidationResult invalidResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(invalidResult);

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(statementRepository, encryptionService);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return LINK_EXPIRED_OR_USED when link is expired")
    void validateAndStreamDetailed_ExpiredLink() {
        // Given
        LinkValidationResult expiredResult = LinkValidationResult.expired(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(expiredResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.LINK_EXPIRED_OR_USED);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("ACC123456"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return LINK_EXPIRED_OR_USED when link is already used")
    void validateAndStreamDetailed_UsedLink() {
        // Given
        LinkValidationResult usedResult = LinkValidationResult.used(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(usedResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.LINK_EXPIRED_OR_USED);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("ACC123456"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return STATEMENT_NOT_FOUND when link not found")
    void validateAndStreamDetailed_LinkNotFound() {
        // Given
        LinkValidationResult notFoundResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(notFoundResult);

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return STATEMENT_NOT_FOUND when statement not in database")
    void validateAndStreamDetailed_StatementNotFound() {
        // Given
        LinkValidationResult validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.empty());

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(statementRepository).findById(testStatementId);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        isNull(),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return FILE_MISSING when file does not exist")
    void validateAndStreamDetailed_FileMissing() {
        // Given
        testStatement.setFilePath("/non/existent/path/file.pdf");
        LinkValidationResult validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.FILE_MISSING);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(statementRepository).findById(testStatementId);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("ACC123456"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should return DECRYPTION_FAILED on decryption error")
    void validateAndStreamDetailed_DecryptionFailed() throws Exception {
        // Given
        File tempFile = tempDir.resolve("test-statement.pdf.enc").toFile();
        Files.write(tempFile.toPath(), "encrypted content".getBytes());
        testStatement.setFilePath(tempFile.getAbsolutePath());

        LinkValidationResult validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));
        when(encryptionService.decryptFileToStream(any(File.class)))
                .thenThrow(new RuntimeException("Decryption error"));

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.DECRYPTION_FAILED);
        assertThat(result.stream()).isEmpty();

        verify(signedLinkService).validateAndConsume(testToken);
        verify(statementRepository).findById(testStatementId);
        verify(encryptionService).decryptFileToStream(any(File.class));
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("ACC123456"),
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should still return stream even if audit recording fails")
    void validateAndStreamDetailed_AuditFailureDoesNotPreventDownload() throws Exception {
        // Given
        File tempFile = tempDir.resolve("test-statement.pdf.enc").toFile();
        Files.write(tempFile.toPath(), "encrypted content".getBytes());
        testStatement.setFilePath(tempFile.getAbsolutePath());

        LinkValidationResult validResult = LinkValidationResult.valid(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(validResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        InputStream mockStream = new ByteArrayInputStream("decrypted content".getBytes());
        when(encryptionService.decryptFileToStream(any(File.class))).thenReturn(mockStream);

        // Audit service throws exception
        doThrow(new RuntimeException("Audit failure"))
                .when(auditService)
                .record(any(), any(), any(), any(), any(), any());

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then - download should still succeed
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.OK);
        assertThat(result.stream()).isPresent();
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should handle null clientIp gracefully")
    void validateAndStreamDetailed_NullClientIp() {
        // Given
        LinkValidationResult invalidResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(invalidResult);

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, null, testUserAgent, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should handle null userAgent gracefully")
    void validateAndStreamDetailed_NullUserAgent() {
        // Given
        LinkValidationResult invalidResult = LinkValidationResult.notFound();
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(invalidResult);

        // When
        DownloadStreamResult result =
                downloadService.validateAndStreamDetailed(testToken, testClientIp, null, testPerformedBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.outcome()).isEqualTo(DownloadOutcome.STATEMENT_NOT_FOUND);
        verify(auditService).record(any(), any(), any(), any(), any(), any(Map.class));
    }

    @Test
    @DisplayName("validateAndStreamDetailed - should fetch account number when statement exists")
    void validateAndStreamDetailed_FetchesAccountNumber() {
        // Given
        LinkValidationResult expiredResult = LinkValidationResult.expired(testLink);
        when(signedLinkService.validateAndConsume(testToken)).thenReturn(expiredResult);
        when(statementRepository.findById(testStatementId)).thenReturn(Optional.of(testStatement));

        // When
        downloadService.validateAndStreamDetailed(testToken, testClientIp, testUserAgent, testPerformedBy);

        // Then
        verify(statementRepository).findById(testStatementId);
        verify(auditService)
                .record(
                        eq(AuditAction.DOWNLOAD_FAILED.getValue()),
                        eq(testStatementId),
                        eq("ACC123456"), // Account number should be included
                        eq(testLinkId),
                        eq(testPerformedBy),
                        any(Map.class));
    }
}
