package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.api.AuditLogEntry;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.model.dto.AuditLogDto;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("AuditApiMapper Tests")
class AuditApiMapperTest {

    @Autowired
    private AuditApiMapper auditApiMapper;

    @Test
    @DisplayName("toApi - should map all fields from DTO to API model")
    void toApi_AllFields() {
        // Given
        UUID id = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        OffsetDateTime performedAt = OffsetDateTime.now();
        Map<String, Object> details = new HashMap<>();
        details.put("key1", "value1");
        details.put("key2", 123);

        AuditLogDto dto = new AuditLogDto();
        dto.setId(id);
        dto.setAccountNumber("ACC123456");
        dto.setStatementId(statementId);
        dto.setAction("DOWNLOAD_SUCCESS");
        dto.setPerformedAt(performedAt);
        dto.setIpAddress("192.168.1.1");
        dto.setUserAgent("Mozilla/5.0");
        dto.setDetails(details);

        // When
        AuditLogEntry result = auditApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("ACC123456");
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getAction()).isEqualTo("DOWNLOAD_SUCCESS");
        assertThat(result.getTimestamp()).isEqualTo(performedAt);
        assertThat(result.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(result.getDetails()).isEqualTo(details);
    }

    @Test
    @DisplayName("toApi - should handle null DTO")
    void toApi_NullDto() {
        // When
        AuditLogEntry result = auditApiMapper.toApi(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApi - should handle DTO with null fields")
    void toApi_NullFields() {
        // Given
        AuditLogDto dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("TEST_ACTION");
        // All other fields are null

        // When
        AuditLogEntry result = auditApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getAction()).isEqualTo("TEST_ACTION");
        assertThat(result.getAccountNumber()).isNull();
        assertThat(result.getStatementId()).isNull();
        assertThat(result.getIpAddress()).isNull();
        assertThat(result.getUserAgent()).isNull();
        // MapStruct initializes null collection fields as empty collections
        if (result.getDetails() != null) {
            assertThat(result.getDetails()).isEmpty();
        }
    }

    @Test
    @DisplayName("toApi - should handle empty details map")
    void toApi_EmptyDetails() {
        // Given
        AuditLogDto dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("TEST_ACTION");
        dto.setDetails(new HashMap<>());

        // When
        AuditLogEntry result = auditApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("toApi - should map performedAt to timestamp")
    void toApi_TimestampMapping() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogDto dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("TEST_ACTION");
        dto.setPerformedAt(now);

        // When
        AuditLogEntry result = auditApiMapper.toApi(dto);

        // Then
        assertThat(result.getTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("toPage - should map list of DTOs to page with content")
    void toPage_WithContent() {
        // Given
        AuditLogDto dto1 = createAuditLogDto("ACTION1", "ACC1");
        AuditLogDto dto2 = createAuditLogDto("ACTION2", "ACC2");
        AuditLogDto dto3 = createAuditLogDto("ACTION3", "ACC3");
        List<AuditLogDto> dtos = Arrays.asList(dto1, dto2, dto3);

        // When
        AuditLogPage result = auditApiMapper.toPage(dtos);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("ACTION1");
        assertThat(result.getContent().get(1).getAction()).isEqualTo("ACTION2");
        assertThat(result.getContent().get(2).getAction()).isEqualTo("ACTION3");
    }

    @Test
    @DisplayName("toPage - should handle null list")
    void toPage_NullList() {
        // When
        AuditLogPage result = auditApiMapper.toPage(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("toPage - should handle empty list")
    void toPage_EmptyList() {
        // Given
        List<AuditLogDto> emptyList = Collections.emptyList();

        // When
        AuditLogPage result = auditApiMapper.toPage(emptyList);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("toPage - should handle single item list")
    void toPage_SingleItem() {
        // Given
        AuditLogDto dto = createAuditLogDto("SINGLE_ACTION", "ACC123");
        List<AuditLogDto> dtos = Collections.singletonList(dto);

        // When
        AuditLogPage result = auditApiMapper.toPage(dtos);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("SINGLE_ACTION");
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo("ACC123");
    }

    @Test
    @DisplayName("toPage - should handle large list")
    void toPage_LargeList() {
        // Given
        List<AuditLogDto> dtos = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            dtos.add(createAuditLogDto("ACTION" + i, "ACC" + i));
        }

        // When
        AuditLogPage result = auditApiMapper.toPage(dtos);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(100);
    }

    @Test
    @DisplayName("toPage - should preserve all DTO properties in mapped entries")
    void toPage_PreservesAllProperties() {
        // Given
        UUID id = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        Map<String, Object> details = Collections.singletonMap("reason", "test");

        AuditLogDto dto = new AuditLogDto();
        dto.setId(id);
        dto.setAccountNumber("ACC999");
        dto.setStatementId(statementId);
        dto.setAction("UPLOAD_SUCCESS");
        dto.setPerformedAt(timestamp);
        dto.setIpAddress("10.0.0.1");
        dto.setUserAgent("TestAgent");
        dto.setDetails(details);

        List<AuditLogDto> dtos = Collections.singletonList(dto);

        // When
        AuditLogPage result = auditApiMapper.toPage(dtos);

        // Then
        assertThat(result.getContent()).hasSize(1);
        AuditLogEntry entry = result.getContent().get(0);
        assertThat(entry.getId()).isEqualTo(id);
        assertThat(entry.getAccountNumber()).isEqualTo("ACC999");
        assertThat(entry.getStatementId()).isEqualTo(statementId);
        assertThat(entry.getAction()).isEqualTo("UPLOAD_SUCCESS");
        assertThat(entry.getTimestamp()).isEqualTo(timestamp);
        assertThat(entry.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(entry.getUserAgent()).isEqualTo("TestAgent");
        assertThat(entry.getDetails()).isEqualTo(details);
    }

    @Test
    @DisplayName("toPage - should handle list with null elements")
    void toPage_WithNullElements() {
        // Given
        AuditLogDto dto1 = createAuditLogDto("ACTION1", "ACC1");
        List<AuditLogDto> dtos = Arrays.asList(dto1, null, createAuditLogDto("ACTION2", "ACC2"));

        // When
        AuditLogPage result = auditApiMapper.toPage(dtos);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0)).isNotNull();
        assertThat(result.getContent().get(1)).isNull();
        assertThat(result.getContent().get(2)).isNotNull();
    }

    @Test
    @DisplayName("toApi - should handle complex details map")
    void toApi_ComplexDetails() {
        // Given
        Map<String, Object> complexDetails = new HashMap<>();
        complexDetails.put("string", "value");
        complexDetails.put("number", 42);
        complexDetails.put("boolean", true);
        complexDetails.put("nested", Collections.singletonMap("key", "value"));
        complexDetails.put("list", Arrays.asList(1, 2, 3));

        AuditLogDto dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction("COMPLEX_ACTION");
        dto.setDetails(complexDetails);

        // When
        AuditLogEntry result = auditApiMapper.toApi(dto);

        // Then
        assertThat(result.getDetails()).isEqualTo(complexDetails);
        assertThat(result.getDetails().get("string")).isEqualTo("value");
        assertThat(result.getDetails().get("number")).isEqualTo(42);
        assertThat(result.getDetails().get("boolean")).isEqualTo(true);
    }

    // Helper method
    private AuditLogDto createAuditLogDto(String action, String accountNumber) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setAction(action);
        dto.setAccountNumber(accountNumber);
        dto.setPerformedAt(OffsetDateTime.now());
        dto.setIpAddress("192.168.1.1");
        dto.setUserAgent("TestAgent");
        return dto;
    }
}
