package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.model.dto.UploadResponseDto;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("UploadResponseApiMapper Tests")
class UploadResponseApiMapperTest {

    private final UploadResponseApiMapper uploadResponseApiMapper = Mappers.getMapper(UploadResponseApiMapper.class);

    @Test
    @DisplayName("toApi - should map all fields from DTO to API model")
    void toApi_AllFields() {
        // Given
        UUID statementId = UUID.randomUUID();
        OffsetDateTime uploadedAt = OffsetDateTime.now();

        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(statementId)
                .uploadedAt(uploadedAt)
                .fileSize(2048L)
                .fileName("statement.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
    }

    @Test
    @DisplayName("toApi - should handle null DTO")
    void toApi_NullDto() {
        // When
        UploadResponse result = uploadResponseApiMapper.toApi(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toApi - should handle DTO with null fields")
    void toApi_NullFields() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .fileName("test.pdf")
                // uploadedAt and fileSize are null
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getUploadedAt()).isNull();
        assertThat(result.getFileSize()).isNull();
    }

    @Test
    @DisplayName("toApi - should handle zero file size")
    void toApi_ZeroFileSize() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(0L)
                .fileName("empty.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toApi - should handle large file size")
    void toApi_LargeFileSize() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(10_737_418_240L) // 10 GB
                .fileName("large.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileSize()).isEqualTo(10_737_418_240L);
    }

    @Test
    @DisplayName("toApi - should preserve timestamp precision")
    void toApi_TimestampPrecision() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(now)
                .fileSize(1024L)
                .fileName("test.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getUploadedAt()).isEqualTo(now);
        assertThat(result.getUploadedAt().getNano()).isEqualTo(now.getNano());
    }

    @Test
    @DisplayName("toApi - should handle special characters in file name")
    void toApi_SpecialCharactersInFileName() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("statement (copy) [2024].pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo("statement (copy) [2024].pdf");
    }

    @Test
    @DisplayName("toApi - should handle Unicode characters in file name")
    void toApi_UnicodeInFileName() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("报表-2024.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo("报表-2024.pdf");
    }

    @Test
    @DisplayName("toApi - should handle long file name")
    void toApi_LongFileName() {
        // Given
        String longFileName = "very_long_statement_file_name_with_many_characters_"
                + "and_underscores_and_numbers_12345678901234567890.pdf";

        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName(longFileName)
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo(longFileName);
    }

    @Test
    @DisplayName("toApi - should handle different UUID values")
    void toApi_DifferentUUIDs() {
        // Given
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        UploadResponseDto dto1 = UploadResponseDto.builder()
                .statementId(uuid1)
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("file1.pdf")
                .build();

        UploadResponseDto dto2 = UploadResponseDto.builder()
                .statementId(uuid2)
                .uploadedAt(OffsetDateTime.now())
                .fileSize(2048L)
                .fileName("file2.pdf")
                .build();

        // When
        UploadResponse result1 = uploadResponseApiMapper.toApi(dto1);
        UploadResponse result2 = uploadResponseApiMapper.toApi(dto2);

        // Then
        assertThat(result1.getStatementId()).isEqualTo(uuid1);
        assertThat(result2.getStatementId()).isEqualTo(uuid2);
        assertThat(result1.getStatementId()).isNotEqualTo(result2.getStatementId());
    }

    @Test
    @DisplayName("toApi - should handle different timestamps")
    void toApi_DifferentTimestamps() {
        // Given
        OffsetDateTime time1 = OffsetDateTime.now();
        OffsetDateTime time2 = time1.plusHours(1);

        UploadResponseDto dto1 = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(time1)
                .fileSize(1024L)
                .fileName("file1.pdf")
                .build();

        UploadResponseDto dto2 = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(time2)
                .fileSize(1024L)
                .fileName("file2.pdf")
                .build();

        // When
        UploadResponse result1 = uploadResponseApiMapper.toApi(dto1);
        UploadResponse result2 = uploadResponseApiMapper.toApi(dto2);

        // Then
        assertThat(result1.getUploadedAt()).isEqualTo(time1);
        assertThat(result2.getUploadedAt()).isEqualTo(time2);
        assertThat(result1.getUploadedAt()).isBefore(result2.getUploadedAt());
    }

    @Test
    @DisplayName("toApi - should handle past timestamps")
    void toApi_PastTimestamp() {
        // Given
        OffsetDateTime pastTime = OffsetDateTime.now().minusDays(30);
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(pastTime)
                .fileSize(1024L)
                .fileName("old-statement.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getUploadedAt()).isEqualTo(pastTime);
    }

    @Test
    @DisplayName("toApi - should handle file name with dots")
    void toApi_FileNameWithDots() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("statement.backup.2024.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo("statement.backup.2024.pdf");
    }

    @Test
    @DisplayName("toApi - should handle file name with spaces")
    void toApi_FileNameWithSpaces() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("monthly statement 2024.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo("monthly statement 2024.pdf");
    }

    @Test
    @DisplayName("toApi - should handle minimal DTO with only required fields")
    void toApi_MinimalDto() {
        // Given
        UUID statementId = UUID.randomUUID();
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(statementId)
                .fileName("minimal.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getFileName()).isEqualTo("minimal.pdf");
    }

    @Test
    @DisplayName("toApi - should preserve all field values exactly")
    void toApi_PreservesExactValues() {
        // Given
        UUID statementId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        OffsetDateTime uploadedAt = OffsetDateTime.parse("2024-01-15T10:30:45.123456789+00:00");
        Long fileSize = 123456789L;
        String fileName = "exact-test.pdf";

        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(statementId)
                .uploadedAt(uploadedAt)
                .fileSize(fileSize)
                .fileName(fileName)
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getStatementId()).isEqualTo(statementId);
        assertThat(result.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.getFileSize()).isEqualTo(fileSize);
        assertThat(result.getFileName()).isEqualTo(fileName);
    }

    @Test
    @DisplayName("toApi - should handle very small file size")
    void toApi_VerySmallFileSize() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1L)
                .fileName("tiny.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("toApi - should map DTO built with all builder methods")
    void toApi_BuilderPattern() {
        // Given
        UUID id = UUID.randomUUID();
        OffsetDateTime time = OffsetDateTime.now();

        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(id)
                .uploadedAt(time)
                .fileSize(4096L)
                .fileName("builder-test.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(id);
        assertThat(result.getUploadedAt()).isEqualTo(time);
        assertThat(result.getFileSize()).isEqualTo(4096L);
        assertThat(result.getFileName()).isEqualTo("builder-test.pdf");
    }

    @Test
    @DisplayName("toApi - should handle file name with path separators")
    void toApi_FileNameWithPathSeparators() {
        // Given
        UploadResponseDto dto = UploadResponseDto.builder()
                .statementId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .fileSize(1024L)
                .fileName("2024/01/statement.pdf")
                .build();

        // When
        UploadResponse result = uploadResponseApiMapper.toApi(dto);

        // Then
        assertThat(result.getFileName()).isEqualTo("2024/01/statement.pdf");
    }
}
