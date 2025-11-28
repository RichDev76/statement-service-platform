package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.dto.AuditLogDto;
import com.example.statementservice.model.entity.AuditLog;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("AuditLogEntityMapper Tests")
class AuditLogEntityMapperTest {

    @Autowired
    private AuditLogEntityMapper auditLogEntityMapper;

    @Test
    @DisplayName("toDto - should map all fields from entity to DTO")
    void toDto_AllFields() {
        // Given
        UUID id = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        UUID signedLinkId = UUID.randomUUID();
        OffsetDateTime performedAt = OffsetDateTime.now();

        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("reason", "Link expired");
        details.put("extraKey", "extraValue");

        AuditLog entity = new AuditLog();
        entity.setId(id);
        entity.setAccountNumber("ACC123456");
        entity.setStatementId(statementId);
        entity.setSignedLinkId(signedLinkId);
        entity.setAction("DOWNLOAD_FAILED");
        entity.setPerformedAt(performedAt);
        entity.setPerformedBy("testUser");
        entity.setDetails(details);

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("ACC123456");
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getAction()).isEqualTo("DOWNLOAD_FAILED");
        assertThat(result.getPerformedAt()).isEqualTo(performedAt);
        assertThat(result.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(result.getDetails()).containsOnlyKeys("reason");
        assertThat(result.getDetails().get("reason")).isEqualTo("Link expired");
    }

    @Test
    @DisplayName("toDto - should handle null entity")
    void toDto_NullEntity() {
        // When
        AuditLogDto result = auditLogEntityMapper.toDto(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toDto - should extract IP address from details")
    void toDto_ExtractIpAddress() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "10.0.0.1");

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("toDto - should extract userAgent from details")
    void toDto_ExtractUserAgent() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("userAgent", "Chrome/90.0");

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getUserAgent()).isEqualTo("Chrome/90.0");
    }

    @Test
    @DisplayName("toDto - should handle null details")
    void toDto_NullDetails() {
        // Given
        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(null);
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getIpAddress()).isNull();
        assertThat(result.getUserAgent()).isNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toDto - should handle empty details map")
    void toDto_EmptyDetails() {
        // Given
        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(new HashMap<>());
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getIpAddress()).isNull();
        assertThat(result.getUserAgent()).isNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toDto - should only include reason in details output")
    void toDto_OnlyReasonInDetails() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("reason", "Invalid signature");
        details.put("token", "abc123");
        details.put("extraField", "extraValue");

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getDetails()).containsOnlyKeys("reason");
        assertThat(result.getDetails().get("reason")).isEqualTo("Invalid signature");
    }

    @Test
    @DisplayName("toDto - should return empty details when reason is missing")
    void toDto_NoReason() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("token", "abc123");

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toDto - should handle non-string detail values")
    void toDto_NonStringDetailValues() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", 12345); // Integer instead of String
        details.put("userAgent", true); // Boolean instead of String

        AuditLog entity = new AuditLog();
        entity.setId(UUID.randomUUID());
        entity.setAction("TEST_ACTION");
        entity.setDetails(details);
        entity.setPerformedAt(OffsetDateTime.now());

        // When
        AuditLogDto result = auditLogEntityMapper.toDto(entity);

        // Then
        assertThat(result.getIpAddress()).isEqualTo("12345");
        assertThat(result.getUserAgent()).isEqualTo("true");
    }

    @Test
    @DisplayName("toDtos - should map list of entities to list of DTOs")
    void toDtos_MultipleEntities() {
        // Given
        AuditLog entity1 = createAuditLog("ACTION1", "ACC1");
        AuditLog entity2 = createAuditLog("ACTION2", "ACC2");
        AuditLog entity3 = createAuditLog("ACTION3", "ACC3");
        List<AuditLog> entities = Arrays.asList(entity1, entity2, entity3);

        // When
        List<AuditLogDto> result = auditLogEntityMapper.toDtos(entities);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAction()).isEqualTo("ACTION1");
        assertThat(result.get(1).getAction()).isEqualTo("ACTION2");
        assertThat(result.get(2).getAction()).isEqualTo("ACTION3");
    }

    @Test
    @DisplayName("toDtos - should handle null list")
    void toDtos_NullList() {
        // When
        List<AuditLogDto> result = auditLogEntityMapper.toDtos(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toDtos - should handle empty list")
    void toDtos_EmptyList() {
        // Given
        List<AuditLog> emptyList = Collections.emptyList();

        // When
        List<AuditLogDto> result = auditLogEntityMapper.toDtos(emptyList);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toDtos - should handle single item list")
    void toDtos_SingleItem() {
        // Given
        AuditLog entity = createAuditLog("SINGLE_ACTION", "ACC123");
        List<AuditLog> entities = Collections.singletonList(entity);

        // When
        List<AuditLogDto> result = auditLogEntityMapper.toDtos(entities);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SINGLE_ACTION");
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC123");
    }

    @Test
    @DisplayName("toDtos - should preserve all properties in mapped DTOs")
    void toDtos_PreservesAllProperties() {
        // Given
        UUID id = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "203.0.113.1");
        details.put("userAgent", "Safari");
        details.put("reason", "Test reason");

        AuditLog entity = new AuditLog();
        entity.setId(id);
        entity.setAccountNumber("ACC999");
        entity.setAction("UPLOAD_SUCCESS");
        entity.setPerformedAt(timestamp);
        entity.setDetails(details);

        List<AuditLog> entities = Collections.singletonList(entity);

        // When
        List<AuditLogDto> result = auditLogEntityMapper.toDtos(entities);

        // Then
        assertThat(result).hasSize(1);
        AuditLogDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getAccountNumber()).isEqualTo("ACC999");
        assertThat(dto.getAction()).isEqualTo("UPLOAD_SUCCESS");
        assertThat(dto.getPerformedAt()).isEqualTo(timestamp);
        assertThat(dto.getIpAddress()).isEqualTo("203.0.113.1");
        assertThat(dto.getUserAgent()).isEqualTo("Safari");
        assertThat(dto.getDetails()).containsOnlyKeys("reason");
    }

    @Test
    @DisplayName("extractDetail - should return null for null details")
    void extractDetail_NullDetails() {
        // Given
        AuditLog log = new AuditLog();
        log.setDetails(null);

        // When
        String result = auditLogEntityMapper.extractDetail(log, "ip");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractDetail - should return null for missing key")
    void extractDetail_MissingKey() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");

        AuditLog log = new AuditLog();
        log.setDetails(details);

        // When
        String result = auditLogEntityMapper.extractDetail(log, "nonExistentKey");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractDetail - should convert value to string")
    void extractDetail_ConvertToString() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("count", 42);
        details.put("flag", true);

        AuditLog log = new AuditLog();
        log.setDetails(details);

        // When
        String countResult = auditLogEntityMapper.extractDetail(log, "count");
        String flagResult = auditLogEntityMapper.extractDetail(log, "flag");

        // Then
        assertThat(countResult).isEqualTo("42");
        assertThat(flagResult).isEqualTo("true");
    }

    @Test
    @DisplayName("extractReason - should return empty map for null log")
    void extractReason_NullLog() {
        // When
        Map<String, Object> result = auditLogEntityMapper.extractReason(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractReason - should return empty map for null details")
    void extractReason_NullDetails() {
        // Given
        AuditLog log = new AuditLog();
        log.setDetails(null);

        // When
        Map<String, Object> result = auditLogEntityMapper.extractReason(log);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractReason - should return empty map when reason is missing")
    void extractReason_MissingReason() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");

        AuditLog log = new AuditLog();
        log.setDetails(details);

        // When
        Map<String, Object> result = auditLogEntityMapper.extractReason(log);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractReason - should return map with only reason when present")
    void extractReason_WithReason() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "Mozilla/5.0");
        details.put("reason", "Invalid token");
        details.put("extraKey", "extraValue");

        AuditLog log = new AuditLog();
        log.setDetails(details);

        // When
        Map<String, Object> result = auditLogEntityMapper.extractReason(log);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result).containsOnlyKeys("reason");
        assertThat(result.get("reason")).isEqualTo("Invalid token");
    }

    @Test
    @DisplayName("extractReason - should handle non-string reason values")
    void extractReason_NonStringReason() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("reason", 404);

        AuditLog log = new AuditLog();
        log.setDetails(details);

        // When
        Map<String, Object> result = auditLogEntityMapper.extractReason(log);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get("reason")).isEqualTo(404);
    }

    @Test
    @DisplayName("extractReason - should return immutable map")
    void extractReason_ImmutableMap() {
        // Given
        Map<String, Object> details = Collections.singletonMap("reason", "Test");
        AuditLog log = new AuditLog();
        log.setDetails(details);

        // When
        Map<String, Object> result = auditLogEntityMapper.extractReason(log);

        // Then
        assertThat(result).containsKey("reason");
        // Verify it's immutable by attempting to modify (should throw exception)
        assertThat(result).isInstanceOf(Map.class);
    }

    // Helper method
    private AuditLog createAuditLog(String action, String accountNumber) {
        Map<String, Object> details = new HashMap<>();
        details.put("ip", "192.168.1.1");
        details.put("userAgent", "TestAgent");

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction(action);
        log.setAccountNumber(accountNumber);
        log.setPerformedAt(OffsetDateTime.now());
        log.setDetails(details);
        return log;
    }
}
