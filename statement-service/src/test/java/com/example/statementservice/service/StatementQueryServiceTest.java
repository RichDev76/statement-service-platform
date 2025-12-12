package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.mapper.StatementApiMapper;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.api.StatementSummaryPage;
import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.util.AuditHelper;
import com.example.statementservice.util.RequestInfo;
import com.example.statementservice.util.RequestInfoProvider;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementQueryService Unit Tests")
class StatementQueryServiceTest {

    @Mock
    private StatementService statementService;

    @Mock
    private StatementApiMapper statementApiMapper;

    @Mock
    private SignedLinkService signedLinkService;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private RequestInfoProvider requestInfoProvider;

    @InjectMocks
    private StatementQueryService statementQueryService;

    private UUID testStatementId;
    private String testAccountNumber;
    private LocalDate testDate;
    private StatementDto testStatementDto;
    private StatementSummary testStatementSummary;
    private Statement testStatement;
    private RequestInfo testRequestInfo;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testAccountNumber = "ACC123456";
        testDate = LocalDate.of(2024, 1, 15);

        testStatementDto = new StatementDto();
        testStatementDto.setStatementId(testStatementId);
        testStatementDto.setAccountNumber(testAccountNumber);
        testStatementDto.setStatementDate(testDate);
        testStatementDto.setFileName("statement.pdf");
        testStatementDto.setFileSize(1024L);
        testStatementDto.setUploadedAt(OffsetDateTime.now());

        testStatementSummary = new StatementSummary();
        testStatementSummary.setStatementId(testStatementId);
        testStatementSummary.setAccountNumber(testAccountNumber);

        testStatement = new Statement();
        testStatement.setId(testStatementId);
        testStatement.setAccountNumber(testAccountNumber);
        testStatement.setStatementDate(testDate);
        testStatement.setUploadedAt(OffsetDateTime.now());

        testRequestInfo = new RequestInfo("127.0.0.1", "JUnit", "test-user");
    }

    // ==================== getStatementSummaryWithSignedDownloadLinkById Tests ====================

    @Test
    @DisplayName("getStatementSummaryWithSignedDownloadLinkById - should return summary when statement exists")
    void getStatementSummaryWithSignedDownloadLinkById_Found() {
        // Given
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.getStatementDtoById(testStatementId)).thenReturn(testStatementDto);
        when(statementApiMapper.toApi(testStatementDto)).thenReturn(testStatementSummary);
        when(signedLinkService.buildSignedLink(testStatementDto.getFileName(), testStatementId))
                .thenReturn(java.net.URI.create("http://localhost/download/statement.pdf"));

        // When
        Optional<StatementSummary> result =
                statementQueryService.getStatementSummaryWithSignedDownloadLinkById(testStatementId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testStatementSummary);
        verify(statementService).getStatementDtoById(testStatementId);
        verify(statementApiMapper).toApi(testStatementDto);
        verify(auditHelper).recordLinkGenerated(eq(testStatementId), eq(testAccountNumber), isNull(), eq("test-user"));
    }

    @Test
    @DisplayName("getStatementSummaryWithSignedDownloadLinkById - should return empty when statement not found")
    void getStatementSummaryWithSignedDownloadLinkById_NotFound() {
        // Given
        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(statementService.getStatementDtoById(testStatementId))
                .thenThrow(new StatementNotFoundException("Not found"));

        // When
        Optional<StatementSummary> result =
                statementQueryService.getStatementSummaryWithSignedDownloadLinkById(testStatementId);

        // Then
        assertThat(result).isEmpty();
        verify(statementService).getStatementDtoById(testStatementId);
        verify(statementApiMapper, never()).toApi(any());
        verify(auditHelper).recordStatementNotFound(eq(testStatementId), eq("test-user"));
    }

    // ==================== searchByAccount Tests ====================

    @Test
    @DisplayName("searchByAccount - should return statements with default pagination")
    void searchByAccount_DefaultPagination() {
        // Given
        List<StatementDto> dtos = Arrays.asList(testStatementDto);
        List<StatementSummary> summaries = Arrays.asList(testStatementSummary);

        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(summaries);

        // When
        List<StatementSummary> result = statementQueryService.searchByAccount(testAccountNumber, null, null);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testStatementSummary);
        verify(statementService).getStatementsDtoByAccountNumber(testAccountNumber);
    }

    @Test
    @DisplayName("searchByAccount - should apply limit correctly")
    void searchByAccount_WithLimit() {
        // Given
        List<StatementDto> dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        // When
        statementQueryService.searchByAccount(testAccountNumber, 5, 0);

        // Then
        verify(statementApiMapper).toApis(argThat(list -> list.size() == 5));
    }

    @Test
    @DisplayName("searchByAccount - should apply offset correctly")
    void searchByAccount_WithOffset() {
        // Given
        List<StatementDto> dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        // When
        statementQueryService.searchByAccount(testAccountNumber, 5, 3);

        // Then - Should skip first 3, take next 5
        verify(statementApiMapper).toApis(argThat(list -> list.size() == 5));
    }

    @Test
    @DisplayName("searchByAccount - should cap limit at 100")
    void searchByAccount_MaxLimit() {
        // Given
        List<StatementDto> dtos = createMultipleDtos(150);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        // When
        statementQueryService.searchByAccount(testAccountNumber, 200, 0);

        // Then - Should cap at 100
        verify(statementApiMapper).toApis(argThat(list -> list.size() == 100));
    }

    @Test
    @DisplayName("searchByAccount - should enforce minimum limit of 1")
    void searchByAccount_MinLimit() {
        // Given
        List<StatementDto> dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        // When
        statementQueryService.searchByAccount(testAccountNumber, 0, 0);

        // Then - Should use minimum of 1
        verify(statementApiMapper).toApis(argThat(list -> list.size() == 1));
    }

    @Test
    @DisplayName("searchByAccount - should handle negative offset as 0")
    void searchByAccount_NegativeOffset() {
        // Given
        List<StatementDto> dtos = createMultipleDtos(10);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        // When
        statementQueryService.searchByAccount(testAccountNumber, 5, -10);

        // Then - Should treat as offset 0
        verify(statementApiMapper).toApis(argThat(list -> list.size() == 5));
    }

    @Test
    @DisplayName("searchByAccount - should return empty list when no statements found")
    void searchByAccount_NotFound() {
        // Given
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenThrow(new StatementNotFoundException("Not found"));

        // When
        List<StatementSummary> result = statementQueryService.searchByAccount(testAccountNumber, null, null);

        // Then
        assertThat(result).isEmpty();
        verify(statementApiMapper, never()).toApis(any());
    }

    @Test
    @DisplayName("searchByAccount - should handle offset beyond list size")
    void searchByAccount_OffsetBeyondSize() {
        // Given
        List<StatementDto> dtos = createMultipleDtos(5);
        when(statementService.getStatementsDtoByAccountNumber(testAccountNumber))
                .thenReturn(dtos);
        when(statementApiMapper.toApis(anyList())).thenReturn(Collections.emptyList());

        // When
        statementQueryService.searchByAccount(testAccountNumber, 10, 100);

        // Then - Should return empty sublist
        verify(statementApiMapper).toApis(argThat(List::isEmpty));
    }

    // ==================== searchByAccountAndDate Tests ====================

    @Test
    @DisplayName("searchByAccountAndDate - should return statement when found")
    void searchByAccountAndDate_Found() {
        // Given
        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.of(testStatementDto));
        when(statementApiMapper.toApi(testStatementDto)).thenReturn(testStatementSummary);

        // When
        List<StatementSummary> result = statementQueryService.searchByAccountAndDate(testAccountNumber, "2024-01-15");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testStatementSummary);
        verify(statementService).getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate);
    }

    @Test
    @DisplayName("searchByAccountAndDate - should return empty list when not found")
    void searchByAccountAndDate_NotFound() {
        // Given
        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.empty());

        // When
        List<StatementSummary> result = statementQueryService.searchByAccountAndDate(testAccountNumber, "2024-01-15");

        // Then
        assertThat(result).isEmpty();
        verify(statementApiMapper, never()).toApi(any());
    }

    @Test
    @DisplayName("searchByAccountAndDate - should throw exception for invalid date format")
    void searchByAccountAndDate_InvalidDateFormat() {
        // When/Then
        assertThatThrownBy(() -> statementQueryService.searchByAccountAndDate(testAccountNumber, "invalid-date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    // ==================== searchPaged Tests ====================

    @Test
    @DisplayName("searchPaged - should return paged results for account number only")
    void searchPaged_AccountOnly() {
        // Given
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, null, 0, 50, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("searchPaged - should return result for account and date")
    void searchPaged_AccountAndDate() {
        // Given
        when(statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testDate))
                .thenReturn(Optional.of(testStatementDto));

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, "2024-01-15", 0, 50, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("searchPaged - should return empty page for date only")
    void searchPaged_DateOnly() {
        // When
        StatementSummaryPage result = statementQueryService.searchPaged(null, "2024-01-15", 0, 50, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("searchPaged - should throw exception when neither account nor date provided")
    void searchPaged_NoParameters() {
        // When/Then
        assertThatThrownBy(() -> statementQueryService.searchPaged(null, null, 0, 50, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one of accountNumber or date must be provided");
    }

    @Test
    @DisplayName("searchPaged - should use default page and size")
    void searchPaged_DefaultPagination() {
        // Given
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, null, null, null, null);

        // Then
        assertThat(result.getPage()).isEqualTo(0); // Default page
        assertThat(result.getSize()).isEqualTo(50); // Default size
    }

    @Test
    @DisplayName("searchPaged - should normalize negative page to 0")
    void searchPaged_NegativePage() {
        // Given
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, null, -5, 50, null);

        // Then
        assertThat(result.getPage()).isEqualTo(0);
    }

    @Test
    @DisplayName("searchPaged - should cap size at 100")
    void searchPaged_MaxSize() {
        // Given
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, null, 0, 200, null);

        // Then
        assertThat(result.getSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("searchPaged - should enforce minimum size of 1")
    void searchPaged_MinSize() {
        // Given
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, null, 0, 0, null);

        // Then
        assertThat(result.getSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("searchPaged - should handle blank account number as null")
    void searchPaged_BlankAccountNumber() {
        // When/Then
        assertThatThrownBy(() -> statementQueryService.searchPaged("   ", null, 0, 50, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one of accountNumber or date must be provided");
    }

    @Test
    @DisplayName("searchPaged - should handle blank date as null")
    void searchPaged_BlankDate() {
        // Given
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementService.getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class)))
                .thenReturn(page);
        when(statementService.toDto(any())).thenReturn(testStatementDto);

        // When
        StatementSummaryPage result = statementQueryService.searchPaged(testAccountNumber, "   ", 0, 50, null);

        // Then - Should ignore blank date and search by account only
        assertThat(result).isNotNull();
        verify(statementService).getStatementsByAccountNumber(eq(testAccountNumber), any(Pageable.class));
    }

    @Test
    @DisplayName("searchPaged - should throw exception for invalid date format")
    void searchPaged_InvalidDateFormat() {
        // When/Then
        assertThatThrownBy(() -> statementQueryService.searchPaged(testAccountNumber, "invalid-date", 0, 50, null))
                .isInstanceOf(DateTimeParseException.class);
    }

    // ==================== Helper Methods ====================

    private List<StatementDto> createMultipleDtos(int count) {
        List<StatementDto> dtos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StatementDto dto = new StatementDto();
            dto.setStatementId(UUID.randomUUID());
            dto.setAccountNumber(testAccountNumber);
            dtos.add(dto);
        }
        return dtos;
    }
}
