package com.example.statementservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementEntityMapper Tests")
class StatementEntityMapperTest {

    private final StatementEntityMapper statementEntityMapper = Mappers.getMapper(StatementEntityMapper.class);

    @BeforeEach
    void setUp() {}

    @Test
    @DisplayName("toDto - should map all fields from entity to DTO without download link")
    void toDto_AllFields() {
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

        StatementDto result = statementEntityMapper.toDto(entity);

        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(id);
        assertThat(result.getAccountNumber()).isEqualTo("ACC123456");
        assertThat(result.getStatementDate()).isEqualTo(statementDate);
        assertThat(result.getFileName()).isEqualTo("statement.pdf");
        assertThat(result.getFileSize()).isEqualTo(2048L);
        assertThat(result.getUploadedAt()).isEqualTo(uploadedAt);
        assertThat(result.getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toDto - should handle null entity")
    void toDto_NullEntity() {
        StatementDto result = statementEntityMapper.toDto(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toDto - should map id to statementId")
    void toDto_IdMapping() {
        UUID id = UUID.randomUUID();
        Statement entity = new Statement();
        entity.setId(id);
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        StatementDto result = statementEntityMapper.toDto(entity);
        assertThat(result.getStatementId()).isEqualTo(id);
    }

    @Test
    @DisplayName("toDto - should map uploadFileName to fileName")
    void toDto_FileNameMapping() {
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("original-name.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        StatementDto result = statementEntityMapper.toDto(entity);
        assertThat(result.getFileName()).isEqualTo("original-name.pdf");
    }

    @Test
    @DisplayName("toDto - should map sizeBytes to fileSize")
    void toDto_FileSizeMapping() {
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(4096L);
        entity.setUploadedAt(OffsetDateTime.now());

        StatementDto result = statementEntityMapper.toDto(entity);
        assertThat(result.getFileSize()).isEqualTo(4096L);
    }

    @Test
    @DisplayName("toDtos - should map list of entities using withoutLink variant")
    void toDtos_MultipleEntities() {
        Statement entity1 = createStatement("ACC001", "file1.pdf", 1024L);
        Statement entity2 = createStatement("ACC002", "file2.pdf", 2048L);
        Statement entity3 = createStatement("ACC003", "file3.pdf", 4096L);

        List<Statement> entities = Arrays.asList(entity1, entity2, entity3);
        List<StatementDto> result = statementEntityMapper.toDtos(entities);

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
    }

    @Test
    @DisplayName("toDtos - should handle null list")
    void toDtos_NullList() {
        List<StatementDto> result = statementEntityMapper.toDtos(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toDtos - should handle empty list")
    void toDtos_EmptyList() {
        List<Statement> emptyList = Collections.emptyList();
        List<StatementDto> result = statementEntityMapper.toDtos(emptyList);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toDtos - should handle single item list")
    void toDtos_SingleItem() {
        Statement entity = createStatement("ACC123", "single.pdf", 1024L);
        List<Statement> entities = Collections.singletonList(entity);

        List<StatementDto> result = statementEntityMapper.toDtos(entities);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC123");
        assertThat(result.get(0).getFileName()).isEqualTo("single.pdf");
        assertThat(result.get(0).getDownloadLink()).isNull();
    }

    @Test
    @DisplayName("toDtos - should preserve all properties in mapped DTOs")
    void toDtos_PreservesAllProperties() {
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
        List<StatementDto> result = statementEntityMapper.toDtos(entities);

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
        List<Statement> entities = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            entities.add(createStatement("ACC" + i, "file" + i + ".pdf", 1024L * i));
        }

        List<StatementDto> result = statementEntityMapper.toDtos(entities);

        assertThat(result).hasSize(100);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("ACC0");
        assertThat(result.get(99).getAccountNumber()).isEqualTo("ACC99");
    }

    @Test
    @DisplayName("toDto - should handle entity with null optional fields")
    void toDto_NullOptionalFields() {
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("test.pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        StatementDto result = statementEntityMapper.toDto(entity);

        assertThat(result).isNotNull();
        assertThat(result.getStatementId()).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        assertThat(result.getAccountNumber()).isNull();
        assertThat(result.getStatementDate()).isNull();
    }

    @Test
    @DisplayName("toDto - should handle zero file size")
    void toDto_ZeroFileSize() {
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("empty.pdf");
        entity.setSizeBytes(0L);
        entity.setUploadedAt(OffsetDateTime.now());

        StatementDto result = statementEntityMapper.toDto(entity);
        assertThat(result.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toDto - should handle special characters in file name")
    void toDto_SpecialCharactersInFileName() {
        Statement entity = new Statement();
        entity.setId(UUID.randomUUID());
        entity.setUploadFileName("statement (2024) [final].pdf");
        entity.setSizeBytes(1024L);
        entity.setUploadedAt(OffsetDateTime.now());

        StatementDto result = statementEntityMapper.toDto(entity);
        assertThat(result.getFileName()).isEqualTo("statement (2024) [final].pdf");
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
