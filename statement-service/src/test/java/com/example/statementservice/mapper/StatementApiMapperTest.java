package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.dto.StatementDto;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("StatementApiMapper Tests")
class StatementApiMapperTest {

    private final StatementApiMapper statementApiMapper = Mappers.getMapper(StatementApiMapper.class);

    @Test
    @DisplayName("toApi - should map all fields from DTO to API model")
    void toApi_AllFields() {
        // Given
        UUID statementId = UUID.randomUUID();
        LocalDate statementDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime uploadedAt = OffsetDateTime.now();
        URI downloadLink = URI.create("https://example.com/download/statement.pdf");

        StatementDto dto = new StatementDto();
        dto.setStatementId(statementId);
        dto.setAccountNumber("ACC123456");
        dto.setStatementDate(statementDate);
        dto.setUploadedAt(uploadedAt);
        dto.setFileSize(2048L);
        dto.setFileName("statement.pdf");
        dto.setDownloadLink(downloadLink);

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getAccountNumber()).isEqualTo("ACC123456");
        assertThat(result.getDate()).isEqualTo("2024-01-15");
        assertThat(result.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
        assertThat(result.getDownloadLink()).isEqualTo(downloadLink);
    }

    @Test
    @DisplayName("toApi - should handle null DTO")
    void toApi_NullDto() {
        // When
        StatementSummary result = statementApiMapper.toApi(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApi - should handle DTO with null fields")
    void toApi_NullFields() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileName("test.pdf");
        // All other fields are null

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getAccountNumber()).isNull();
        assertThat(result.getDate()).isNull();
        assertThat(result.getUploadedAt()).isNull();
        assertThat(result.getFileSize()).isNull();
        assertThat(result.getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toApi - should convert LocalDate to String")
    void toApi_DateConversion() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setStatementDate(LocalDate.of(2024, 12, 25));
        dto.setFileName("test.pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getDate()).isEqualTo("2024-12-25");
    }

    @Test
    @DisplayName("toApi - should handle null statement date")
    void toApi_NullStatementDate() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setStatementDate(null);
        dto.setFileName("test.pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getDate()).isNull();
    }

    @Test
    @DisplayName("toApi - should handle different date formats")
    void toApi_DifferentDates() {
        // Given
        StatementDto dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        StatementDto dto2 = createStatementDto(LocalDate.of(2024, 12, 31));
        StatementDto dto3 = createStatementDto(LocalDate.of(2024, 2, 29)); // Leap year

        // When
        StatementSummary result1 = statementApiMapper.toApi(dto1);
        StatementSummary result2 = statementApiMapper.toApi(dto2);
        StatementSummary result3 = statementApiMapper.toApi(dto3);

        // Then
        assertThat(result1.getDate()).isEqualTo("2024-01-01");
        assertThat(result2.getDate()).isEqualTo("2024-12-31");
        assertThat(result3.getDate()).isEqualTo("2024-02-29");
    }

    @Test
    @DisplayName("toApi - should handle zero file size")
    void toApi_ZeroFileSize() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileSize(0L);
        dto.setFileName("empty.pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toApi - should handle large file size")
    void toApi_LargeFileSize() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileSize(10_737_418_240L); // 10 GB
        dto.setFileName("large.pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileSize()).isEqualTo(10_737_418_240L);
    }

    @Test
    @DisplayName("toApi - should preserve download link URI")
    void toApi_DownloadLink() {
        // Given
        URI downloadLink =
                URI.create("https://example.com/api/v1/statements/download/file.pdf?expires=123&signature=abc");
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setDownloadLink(downloadLink);
        dto.setFileName("file.pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getDownloadLink()).isEqualTo(downloadLink);
        assertThat(result.getDownloadLink().toString()).contains("expires=123");
        assertThat(result.getDownloadLink().toString()).contains("signature=abc");
    }

    @Test
    @DisplayName("toApis - should map list of DTOs to list of API models")
    void toApis_MultipleItems() {
        // Given
        StatementDto dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        dto1.setAccountNumber("ACC001");
        StatementDto dto2 = createStatementDto(LocalDate.of(2024, 2, 1));
        dto2.setAccountNumber("ACC002");
        StatementDto dto3 = createStatementDto(LocalDate.of(2024, 3, 1));
        dto3.setAccountNumber("ACC003");

        List<StatementDto> dtos = Arrays.asList(dto1, dto2, dto3);

        // When
        List<StatementSummary> result = statementApiMapper.toApis(dtos);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC001");
        assertThat(result.get(0).getDate()).isEqualTo("2024-01-01");
        assertThat(result.get(1).getAccountNumber()).isEqualTo("ACC002");
        assertThat(result.get(1).getDate()).isEqualTo("2024-02-01");
        assertThat(result.get(2).getAccountNumber()).isEqualTo("ACC003");
        assertThat(result.get(2).getDate()).isEqualTo("2024-03-01");
    }

    @Test
    @DisplayName("toApis - should handle null list")
    void toApis_NullList() {
        // When
        List<StatementSummary> result = statementApiMapper.toApis(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApis - should handle empty list")
    void toApis_EmptyList() {
        // Given
        List<StatementDto> emptyList = Collections.emptyList();

        // When
        List<StatementSummary> result = statementApiMapper.toApis(emptyList);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toApis - should handle single item list")
    void toApis_SingleItem() {
        // Given
        StatementDto dto = createStatementDto(LocalDate.of(2024, 6, 15));
        dto.setAccountNumber("ACC999");
        List<StatementDto> dtos = Collections.singletonList(dto);

        // When
        List<StatementSummary> result = statementApiMapper.toApis(dtos);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC999");
        assertThat(result.get(0).getDate()).isEqualTo("2024-06-15");
    }

    @Test
    @DisplayName("toApis - should handle large list")
    void toApis_LargeList() {
        // Given
        List<StatementDto> dtos = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            StatementDto dto = createStatementDto(LocalDate.of(2024, 1, 1).plusDays(i));
            dto.setAccountNumber("ACC" + i);
            dtos.add(dto);
        }

        // When
        List<StatementSummary> result = statementApiMapper.toApis(dtos);

        // Then
        assertThat(result).hasSize(100);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC0");
        assertThat(result.get(99).getAccountNumber()).isEqualTo("ACC99");
    }

    @Test
    @DisplayName("toApis - should preserve all properties in mapped items")
    void toApis_PreservesAllProperties() {
        // Given
        UUID statementId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 7, 20);
        OffsetDateTime uploadedAt = OffsetDateTime.now();
        URI downloadLink = URI.create("https://example.com/download/test.pdf");

        StatementDto dto = new StatementDto();
        dto.setStatementId(statementId);
        dto.setAccountNumber("ACC555");
        dto.setStatementDate(date);
        dto.setUploadedAt(uploadedAt);
        dto.setFileSize(4096L);
        dto.setFileName("test.pdf");
        dto.setDownloadLink(downloadLink);

        List<StatementDto> dtos = Collections.singletonList(dto);

        // When
        List<StatementSummary> result = statementApiMapper.toApis(dtos);

        // Then
        assertThat(result).hasSize(1);
        StatementSummary summary = result.get(0);
        assertThat(summary.getStatementId()).isEqualTo(statementId);
        assertThat(summary.getAccountNumber()).isEqualTo("ACC555");
        assertThat(summary.getDate()).isEqualTo("2024-07-20");
        assertThat(summary.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(summary.getFileSize()).isEqualTo(4096L);
        assertThat(summary.getFileName()).isEqualTo("test.pdf");
        assertThat(summary.getDownloadLink()).isEqualTo(downloadLink);
    }

    @Test
    @DisplayName("toApis - should handle list with null elements")
    void toApis_WithNullElements() {
        // Given
        StatementDto dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        StatementDto dto2 = createStatementDto(LocalDate.of(2024, 2, 1));
        List<StatementDto> dtos = Arrays.asList(dto1, null, dto2);

        // When
        List<StatementSummary> result = statementApiMapper.toApis(dtos);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isNotNull();
        assertThat(result.get(1)).isNull();
        assertThat(result.get(2)).isNotNull();
    }

    @Test
    @DisplayName("toApis - should handle list with items having null dates")
    void toApis_WithNullDates() {
        // Given
        StatementDto dto1 = createStatementDto(LocalDate.of(2024, 1, 1));
        StatementDto dto2 = createStatementDto(null);
        StatementDto dto3 = createStatementDto(LocalDate.of(2024, 3, 1));

        List<StatementDto> dtos = Arrays.asList(dto1, dto2, dto3);

        // When
        List<StatementSummary> result = statementApiMapper.toApis(dtos);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDate()).isEqualTo("2024-01-01");
        assertThat(result.get(1).getDate()).isNull();
        assertThat(result.get(2).getDate()).isEqualTo("2024-03-01");
    }

    @Test
    @DisplayName("toApi - should handle special characters in file name")
    void toApi_SpecialCharactersInFileName() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setFileName("statement (copy) [2024].pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo("statement (copy) [2024].pdf");
    }

    @Test
    @DisplayName("toApi - should handle Unicode characters in account number")
    void toApi_UnicodeInAccountNumber() {
        // Given
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setAccountNumber("账户123456");
        dto.setFileName("test.pdf");

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getAccountNumber()).isEqualTo("账户123456");
    }

    @Test
    @DisplayName("toApi - should handle past dates")
    void toApi_PastDates() {
        // Given
        StatementDto dto = createStatementDto(LocalDate.of(2020, 1, 1));

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getDate()).isEqualTo("2020-01-01");
    }

    @Test
    @DisplayName("toApi - should handle future dates")
    void toApi_FutureDates() {
        // Given
        StatementDto dto = createStatementDto(LocalDate.of(2030, 12, 31));

        // When
        StatementSummary result = statementApiMapper.toApi(dto);

        // Then
        assertThat(result.getDate()).isEqualTo("2030-12-31");
    }

    // Helper method
    private StatementDto createStatementDto(LocalDate statementDate) {
        StatementDto dto = new StatementDto();
        dto.setStatementId(UUID.randomUUID());
        dto.setAccountNumber("ACC123456");
        dto.setStatementDate(statementDate);
        dto.setUploadedAt(OffsetDateTime.now());
        dto.setFileSize(1024L);
        dto.setFileName("statement.pdf");
        dto.setDownloadLink(URI.create("https://example.com/download/statement.pdf"));
        return dto;
    }
}
