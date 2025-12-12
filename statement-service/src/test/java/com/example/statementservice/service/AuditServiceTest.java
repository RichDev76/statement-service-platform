package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.statementservice.model.entity.AuditLog;
import com.example.statementservice.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Unit Tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    private UUID testStatementId;
    private UUID testSignedLinkId;
    private String testAccountNumber;
    private String testAction;
    private String testPerformedBy;
    private Map<String, Object> testDetails;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);

        testStatementId = UUID.randomUUID();
        testSignedLinkId = UUID.randomUUID();
        testAccountNumber = "ACC123456";
        testAction = "DOWNLOAD_SUCCESS";
        testPerformedBy = "testUser";
        testDetails = new HashMap<>();
        testDetails.put("ip", "192.168.1.1");
        testDetails.put("userAgent", "Mozilla/5.0");
    }

    // ==================== record Tests ====================

    @Test
    @DisplayName("record - should save audit log asynchronously")
    void record_Success() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        auditService.record(
                testAction, testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);

        // Then - Wait for async execution
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getId()).isNotNull();
            assertThat(savedLog.getAction()).isEqualTo(testAction);
            assertThat(savedLog.getStatementId()).isEqualTo(testStatementId);
            assertThat(savedLog.getAccountNumber()).isEqualTo(testAccountNumber);
            assertThat(savedLog.getSignedLinkId()).isEqualTo(testSignedLinkId);
            assertThat(savedLog.getPerformedBy()).isEqualTo(testPerformedBy);
            assertThat(savedLog.getPerformedAt()).isNotNull();
            assertThat(savedLog.getDetails()).isEqualTo(testDetails);
        });
    }

    @Test
    @DisplayName("record - should handle null statement ID")
    void record_NullStatementId() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        auditService.record(testAction, null, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getStatementId()).isNull();
        });
    }

    @Test
    @DisplayName("record - should handle null account number")
    void record_NullAccountNumber() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        auditService.record(testAction, testStatementId, null, testSignedLinkId, testPerformedBy, testDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getAccountNumber()).isNull();
        });
    }

    @Test
    @DisplayName("record - should handle null signed link ID")
    void record_NullSignedLinkId() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        auditService.record(testAction, testStatementId, testAccountNumber, null, testPerformedBy, testDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getSignedLinkId()).isNull();
        });
    }

    @Test
    @DisplayName("record - should handle null details")
    void record_NullDetails() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        auditService.record(testAction, testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, null);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getDetails()).isNull();
        });
    }

    @Test
    @DisplayName("record - should handle empty details map")
    void record_EmptyDetails() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> emptyDetails = new HashMap<>();

        // When
        auditService.record(
                testAction, testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, emptyDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getDetails()).isEmpty();
        });
    }

    @Test
    @DisplayName("record - should handle repository exception gracefully")
    void record_RepositoryException() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Database error"));

        // When - Should not throw exception, just log error
        auditService.record(
                testAction, testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);

        // Then - Wait and verify save was attempted
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(auditLogRepository).save(any(AuditLog.class));
        });

        // No exception should be propagated
    }

    @Test
    @DisplayName("record - should set performedAt timestamp")
    void record_PerformedAtTimestamp() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OffsetDateTime before = OffsetDateTime.now();

        // When
        auditService.record(
                testAction, testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            OffsetDateTime after = OffsetDateTime.now();

            assertThat(savedLog.getPerformedAt()).isNotNull();
            assertThat(savedLog.getPerformedAt()).isAfterOrEqualTo(before.minusSeconds(1));
            assertThat(savedLog.getPerformedAt()).isBeforeOrEqualTo(after.plusSeconds(1));
        });
    }

    @Test
    @DisplayName("record - should generate unique IDs for each audit log")
    void record_UniqueIds() {
        // Given
        List<AuditLog> savedLogs = new ArrayList<>();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog log = invocation.getArgument(0);
            savedLogs.add(log);
            return log;
        });

        // When - Record multiple audit logs
        auditService.record(
                "ACTION1", testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);
        auditService.record(
                "ACTION2", testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);
        auditService.record(
                "ACTION3", testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, testDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(auditLogRepository, times(3)).save(any(AuditLog.class));
        });

        // Verify all IDs are unique
        Set<UUID> uniqueIds = new HashSet<>();
        for (AuditLog log : savedLogs) {
            uniqueIds.add(log.getId());
        }
        assertThat(uniqueIds).hasSize(3);
    }

    @Test
    @DisplayName("record - should handle complex details map")
    void record_ComplexDetails() {
        // Given
        Map<String, Object> complexDetails = new HashMap<>();
        complexDetails.put("ip", "192.168.1.1");
        complexDetails.put("userAgent", "Mozilla/5.0");
        complexDetails.put("attemptCount", 3);
        complexDetails.put("success", true);
        complexDetails.put("metadata", Map.of("key1", "value1", "key2", "value2"));

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        auditService.record(
                testAction, testStatementId, testAccountNumber, testSignedLinkId, testPerformedBy, complexDetails);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog savedLog = captor.getValue();
            assertThat(savedLog.getDetails()).isEqualTo(complexDetails);
            assertThat(savedLog.getDetails()).containsEntry("attemptCount", 3);
            assertThat(savedLog.getDetails()).containsEntry("success", true);
        });
    }

    // ==================== getAllAuditLogs Tests ====================

    @Test
    @DisplayName("getAllAuditLogs - should return all audit logs")
    void getAllAuditLogs_Success() {
        // Given
        AuditLog log1 = createAuditLog("ACTION1");
        AuditLog log2 = createAuditLog("ACTION2");
        List<AuditLog> logs = Arrays.asList(log1, log2);

        when(auditLogRepository.findAll()).thenReturn(logs);

        // When
        List<AuditLog> result = auditService.getAllAuditLogs();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(log1, log2);
        verify(auditLogRepository).findAll();
    }

    @Test
    @DisplayName("getAllAuditLogs - should return empty list when no logs exist")
    void getAllAuditLogs_Empty() {
        // Given
        when(auditLogRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<AuditLog> result = auditService.getAllAuditLogs();

        // Then
        assertThat(result).isEmpty();
        verify(auditLogRepository).findAll();
    }

    @Test
    @DisplayName("getAllAuditLogs - should return multiple audit logs")
    void getAllAuditLogs_Multiple() {
        // Given
        List<AuditLog> logs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(createAuditLog("ACTION" + i));
        }

        when(auditLogRepository.findAll()).thenReturn(logs);

        // When
        List<AuditLog> result = auditService.getAllAuditLogs();

        // Then
        assertThat(result).hasSize(10);
        verify(auditLogRepository).findAll();
    }

    // ==================== Helper Methods ====================

    private AuditLog createAuditLog(String action) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction(action);
        log.setStatementId(testStatementId);
        log.setAccountNumber(testAccountNumber);
        log.setSignedLinkId(testSignedLinkId);
        log.setPerformedBy(testPerformedBy);
        log.setPerformedAt(OffsetDateTime.now());
        log.setDetails(testDetails);
        return log;
    }
}
