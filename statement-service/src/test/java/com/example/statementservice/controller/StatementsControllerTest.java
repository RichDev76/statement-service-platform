package com.example.statementservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.statementservice.exception.InvalidInputException;
import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.model.DownloadOutcome;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.api.StatementSummaryPage;
import com.example.statementservice.service.DownloadService;
import com.example.statementservice.service.StatementQueryService;
import com.example.statementservice.util.DownloadResponseFactory;
import com.example.statementservice.util.RequestInfo;
import com.example.statementservice.util.RequestInfoProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementsController Unit Tests")
class StatementsControllerTest {

    @Mock
    private DownloadService downloadService;

    @Mock
    private StatementQueryService statementQueryService;

    @Mock
    private RequestInfoProvider requestInfoProvider;

    @Mock
    private DownloadResponseFactory downloadResponseFactory;

    @InjectMocks
    private StatementsController statementsController;

    private RequestInfo testRequestInfo;
    private UUID testStatementId;
    private StatementSummary testStatementSummary;
    private StatementSummaryPage testStatementSummaryPage;

    @BeforeEach
    void setUp() {
        testRequestInfo = new RequestInfo("192.168.1.1", "Mozilla/5.0", "testUser");
        testStatementId = UUID.randomUUID();

        testStatementSummary = new StatementSummary();
        testStatementSummary.setStatementId(testStatementId);
        testStatementSummary.setAccountNumber("123456789");

        testStatementSummaryPage = new StatementSummaryPage();
        testStatementSummaryPage.setContent(new ArrayList<>());
        testStatementSummaryPage.setPage(0);
        testStatementSummaryPage.setSize(50);
        testStatementSummaryPage.setTotalElements(0L);
        testStatementSummaryPage.setTotalPages(0);
    }

    @Test
    @DisplayName("downloadStatementByFileName - should return OK response with file content")
    void downloadStatementByFileName_Success() {
        String fileName = "statement-2024-01.pdf";
        Long expires = 1234567890L;
        String signature = "test-signature";

        InputStream testStream = new ByteArrayInputStream("test content".getBytes());
        DownloadService.DownloadStreamResult successResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(testStream));

        Resource resource = new InputStreamResource(testStream);
        ResponseEntity<Resource> expectedResponse = ResponseEntity.ok(resource);

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(signature, expires, "192.168.1.1", "Mozilla/5.0", "testUser"))
                .thenReturn(successResult);
        when(downloadResponseFactory.build(fileName, successResult)).thenReturn(expectedResponse);

        ResponseEntity<Resource> response =
                statementsController.downloadStatementByFileName(fileName, expires, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        verify(requestInfoProvider).get();
        verify(downloadService).validateAndStreamDetailed(signature, expires, "192.168.1.1", "Mozilla/5.0", "testUser");
        verify(downloadResponseFactory).build(fileName, successResult);
    }

    @Test
    @DisplayName("downloadStatementByFileName - should return FORBIDDEN for invalid signature")
    void downloadStatementByFileName_InvalidSignature() {
        String fileName = "statement.pdf";
        Long expires = 1234567890L;
        String signature = "invalid-signature";

        DownloadService.DownloadStreamResult failureResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.INVALID_SIGNATURE, Optional.empty());
        ResponseEntity<Resource> forbiddenResponse =
                ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(failureResult);
        when(downloadResponseFactory.build(fileName, failureResult)).thenReturn(forbiddenResponse);

        ResponseEntity<Resource> response =
                statementsController.downloadStatementByFileName(fileName, expires, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("downloadStatementByFileName - should return NOT_FOUND for expired link")
    void downloadStatementByFileName_ExpiredLink() {
        String fileName = "statement.pdf";
        Long expires = 1234567890L;
        String signature = "expired-signature";

        DownloadService.DownloadStreamResult expiredResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.LINK_EXPIRED_OR_USED, Optional.empty());
        ResponseEntity<Resource> notFoundResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(expiredResult);
        when(downloadResponseFactory.build(fileName, expiredResult)).thenReturn(notFoundResponse);

        ResponseEntity<Resource> response =
                statementsController.downloadStatementByFileName(fileName, expires, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("downloadStatementByFileName - should use request info from provider")
    void downloadStatementByFileName_UsesRequestInfo() {

        String fileName = "statement.pdf";
        Long expires = 1234567890L;
        String signature = "signature";

        RequestInfo customRequestInfo = new RequestInfo("10.0.0.1", "Custom-Agent", "customUser");
        DownloadService.DownloadStreamResult result = new DownloadService.DownloadStreamResult(
                DownloadOutcome.OK, Optional.of(new ByteArrayInputStream(new byte[0])));

        when(requestInfoProvider.get()).thenReturn(customRequestInfo);
        when(downloadService.validateAndStreamDetailed(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(result);
        when(downloadResponseFactory.build(anyString(), any()))
                .thenReturn(ResponseEntity.ok().build());

        statementsController.downloadStatementByFileName(fileName, expires, signature, null);

        verify(downloadService).validateAndStreamDetailed(signature, expires, "10.0.0.1", "Custom-Agent", "customUser");
    }

    @Test
    @DisplayName("downloadStatementByFileName - should pass signature to download service")
    void downloadStatementByFileName_PassesSignature() {

        String fileName = "statement.pdf";
        Long expires = 1234567890L;
        String signature = "specific-signature-value";

        DownloadService.DownloadStreamResult result = new DownloadService.DownloadStreamResult(
                DownloadOutcome.OK, Optional.of(new ByteArrayInputStream(new byte[0])));

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(eq(signature), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(result);
        when(downloadResponseFactory.build(anyString(), any()))
                .thenReturn(ResponseEntity.ok().build());

        statementsController.downloadStatementByFileName(fileName, expires, signature, null);

        verify(downloadService).validateAndStreamDetailed(eq(signature), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("downloadStatementByFileName - should handle statement not found")
    void downloadStatementByFileName_StatementNotFound() {

        String fileName = "statement.pdf";
        Long expires = 1234567890L;
        String signature = "signature";

        DownloadService.DownloadStreamResult notFoundResult =
                new DownloadService.DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        ResponseEntity<Resource> notFoundResponse =
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        when(requestInfoProvider.get()).thenReturn(testRequestInfo);
        when(downloadService.validateAndStreamDetailed(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(notFoundResult);
        when(downloadResponseFactory.build(fileName, notFoundResult)).thenReturn(notFoundResponse);

        ResponseEntity<Resource> response =
                statementsController.downloadStatementByFileName(fileName, expires, signature, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should return OK with statement summary when found")
    void getDownloadSignedLinkById_Found() {

        when(statementQueryService.getStatementSummaryWithSignedDownloadLinkById(testStatementId))
                .thenReturn(Optional.of(testStatementSummary));

        ResponseEntity<StatementSummary> response =
                statementsController.getDownloadSignedLinkById(testStatementId, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(testStatementId);
        assertThat(response.getBody().getAccountNumber()).isEqualTo("123456789");

        verify(statementQueryService).getStatementSummaryWithSignedDownloadLinkById(testStatementId);
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should throw StatementNotFoundException when not found")
    void getDownloadSignedLinkById_NotFound() {

        when(statementQueryService.getStatementSummaryWithSignedDownloadLinkById(testStatementId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> statementsController.getDownloadSignedLinkById(testStatementId, null))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement(s) not found for Id: " + testStatementId);

        verify(statementQueryService).getStatementSummaryWithSignedDownloadLinkById(testStatementId);
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should pass correct statement ID to service")
    void getDownloadSignedLinkById_PassesCorrectId() {

        UUID specificId = UUID.randomUUID();
        StatementSummary summary = new StatementSummary();
        summary.setStatementId(specificId);

        when(statementQueryService.getStatementSummaryWithSignedDownloadLinkById(specificId))
                .thenReturn(Optional.of(summary));

        ResponseEntity<StatementSummary> response = statementsController.getDownloadSignedLinkById(specificId, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatementId()).isEqualTo(specificId);
        verify(statementQueryService).getStatementSummaryWithSignedDownloadLinkById(eq(specificId));
    }

    @Test
    @DisplayName("getDownloadSignedLinkById - should propagate service exceptions")
    void getDownloadSignedLinkById_ServiceException() {

        when(statementQueryService.getStatementSummaryWithSignedDownloadLinkById(any()))
                .thenThrow(new RuntimeException("Service error"));

        assertThatThrownBy(() -> statementsController.getDownloadSignedLinkById(testStatementId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(statementQueryService).getStatementSummaryWithSignedDownloadLinkById(testStatementId);
    }

    @Test
    @DisplayName("searchStatements - should return OK with results when account number provided")
    void searchStatements_WithAccountNumber() {

        String accountNumber = "123456789";
        when(statementQueryService.searchPaged(accountNumber, null, null, null, null))
                .thenReturn(testStatementSummaryPage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, accountNumber, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(testStatementSummaryPage);

        verify(statementQueryService).searchPaged(accountNumber, null, null, null, null);
    }

    @Test
    @DisplayName("searchStatements - should return OK with results when date provided")
    void searchStatements_WithDate() {

        String date = "2024-01-15";
        when(statementQueryService.searchPaged(null, date, null, null, null)).thenReturn(testStatementSummaryPage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, null, date, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(statementQueryService).searchPaged(null, date, null, null, null);
    }

    @Test
    @DisplayName("searchStatements - should return OK when both account number and date provided")
    void searchStatements_WithBothAccountAndDate() {

        String accountNumber = "123456789";
        String date = "2024-01-15";
        when(statementQueryService.searchPaged(accountNumber, date, null, null, null))
                .thenReturn(testStatementSummaryPage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, accountNumber, date, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(statementQueryService).searchPaged(accountNumber, date, null, null, null);
    }

    @Test
    @DisplayName("searchStatements - should throw InvalidInputException when neither account nor date provided")
    void searchStatements_NeitherAccountNorDate() {
        assertThatThrownBy(() -> statementsController.searchStatements(null, null, null, null, null, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("At least one of accountNumber or date must be provided");

        verifyNoInteractions(statementQueryService);
    }

    @Test
    @DisplayName("searchStatements - should throw InvalidInputException when both are blank strings")
    void searchStatements_BothBlankStrings() {
        assertThatThrownBy(() -> statementsController.searchStatements(null, "", "   ", null, null, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("At least one of accountNumber or date must be provided");

        verifyNoInteractions(statementQueryService);
    }

    @Test
    @DisplayName("searchStatements - should accept blank account number if date is provided")
    void searchStatements_BlankAccountWithDate() {

        String date = "2024-01-15";
        when(statementQueryService.searchPaged(anyString(), eq(date), any(), any(), any()))
                .thenReturn(testStatementSummaryPage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, "", date, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("searchStatements - should accept blank date if account number is provided")
    void searchStatements_BlankDateWithAccount() {

        String accountNumber = "123456789";
        when(statementQueryService.searchPaged(eq(accountNumber), anyString(), any(), any(), any()))
                .thenReturn(testStatementSummaryPage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, accountNumber, "   ", null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("searchStatements - should pass pagination parameters to service")
    void searchStatements_WithPagination() {

        String accountNumber = "123456789";
        Integer page = 1;
        Integer size = 25;
        when(statementQueryService.searchPaged(accountNumber, null, page, size, null))
                .thenReturn(testStatementSummaryPage);

        statementsController.searchStatements(null, accountNumber, null, page, size, null);

        verify(statementQueryService).searchPaged(eq(accountNumber), isNull(), eq(page), eq(size), isNull());
    }

    @Test
    @DisplayName("searchStatements - should pass sort parameter to service")
    void searchStatements_WithSort() {

        String accountNumber = "123456789";
        String sort = "uploadedAt:desc";
        when(statementQueryService.searchPaged(accountNumber, null, null, null, sort))
                .thenReturn(testStatementSummaryPage);

        statementsController.searchStatements(null, accountNumber, null, null, null, sort);

        verify(statementQueryService).searchPaged(eq(accountNumber), isNull(), isNull(), isNull(), eq(sort));
    }

    @Test
    @DisplayName("searchStatements - should pass all parameters to service")
    void searchStatements_AllParameters() {

        String accountNumber = "123456789";
        String date = "2024-01-15";
        Integer page = 2;
        Integer size = 50;
        String sort = "statementDate:asc";

        when(statementQueryService.searchPaged(accountNumber, date, page, size, sort))
                .thenReturn(testStatementSummaryPage);

        statementsController.searchStatements(null, accountNumber, date, page, size, sort);

        verify(statementQueryService).searchPaged(eq(accountNumber), eq(date), eq(page), eq(size), eq(sort));
    }

    @Test
    @DisplayName("searchStatements - should propagate service exceptions")
    void searchStatements_ServiceException() {

        String accountNumber = "123456789";
        when(statementQueryService.searchPaged(anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Service error"));

        assertThatThrownBy(() -> statementsController.searchStatements(null, accountNumber, null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");

        verify(statementQueryService).searchPaged(accountNumber, null, null, null, null);
    }

    @Test
    @DisplayName("searchStatements - should handle empty result page")
    void searchStatements_EmptyResults() {

        String accountNumber = "123456789";
        StatementSummaryPage emptyPage = new StatementSummaryPage();
        emptyPage.setContent(new ArrayList<>());
        emptyPage.setTotalElements(0L);

        when(statementQueryService.searchPaged(accountNumber, null, null, null, null))
                .thenReturn(emptyPage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, accountNumber, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("searchStatements - should handle large result sets")
    void searchStatements_LargeResults() {

        String accountNumber = "123456789";
        StatementSummaryPage largePage = new StatementSummaryPage();
        largePage.setContent(new ArrayList<>());
        largePage.setTotalElements(1000L);
        largePage.setTotalPages(20);

        when(statementQueryService.searchPaged(accountNumber, null, null, null, null))
                .thenReturn(largePage);

        ResponseEntity<StatementSummaryPage> response =
                statementsController.searchStatements(null, accountNumber, null, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1000L);
        assertThat(response.getBody().getTotalPages()).isEqualTo(20);
    }
}
