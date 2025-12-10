package com.example.statementservice.service;

import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.mapper.StatementApiMapper;
import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.api.StatementSummaryPage;
import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.util.AuditHelper;
import com.example.statementservice.util.RequestInfoProvider;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementQueryService {

    private final StatementService statementService;
    private final StatementApiMapper statementApiMapper;
    private final SignedLinkService signedLinkService;
    private final AuditHelper auditHelper;
    private final RequestInfoProvider requestInfoProvider;

    public Optional<StatementSummary> getStatementSummaryWithSignedDownloadLinkById(UUID statementId) {
        String performedBy = getPerformedBy();

        try {
            StatementDto dto = this.statementService.getStatementDtoById(statementId);
            String accountNumber = dto.getAccountNumber();

            // Generate download link with audit logging
            try {
                URI downloadLink = signedLinkService.buildSignedLink(dto.getFileName(), statementId);
                dto.setDownloadLink(downloadLink);

                // Audit success - signedLinkId is null unless SignedLinkService is modified to return it
                auditHelper.recordLinkGenerated(statementId, accountNumber, null, performedBy);

            } catch (Exception linkException) {
                // Audit failure for link generation error
                auditHelper.recordLinkGenerationFailed(statementId, accountNumber, performedBy, linkException);
                // Continue without download link (graceful degradation)
            }

            return Optional.of(statementApiMapper.toApi(dto));

        } catch (StatementNotFoundException e) {
            // Audit failure for statement not found
            auditHelper.recordStatementNotFound(statementId, performedBy);
            return Optional.empty();

        } catch (Exception e) {
            // Audit failure for unexpected errors
            auditHelper.recordUnexpectedError(statementId, null, performedBy, e);
            throw e; // Re-throw unexpected exceptions
        }
    }

    private String getPerformedBy() {
        try {
            return requestInfoProvider.get().getPerformedBy();
        } catch (Exception e) {
            log.warn("Failed to get performedBy from request context", e);
            return "system";
        }
    }

    public List<StatementSummary> searchByAccount(String accountNumber, Integer limit, Integer offset) {
        // Apply defaults and bounds as per OpenAPI (limit 1..100, default 50) and offset >= 0
        int safeLimit = (limit == null) ? 50 : Math.max(1, Math.min(100, limit));
        int safeOffset = (offset == null) ? 0 : Math.max(0, offset);

        try {
            var dtos = this.statementService.getStatementsDtoByAccountNumber(accountNumber);
            int fromIndex = Math.min(safeOffset, dtos.size());
            int toIndex = Math.min(fromIndex + safeLimit, dtos.size());
            var page = dtos.subList(fromIndex, toIndex);
            return this.statementApiMapper.toApis(page);
        } catch (StatementNotFoundException e) {
            // Spec allows empty result set
            return new ArrayList<>();
        }
    }

    public List<StatementSummary> searchByAccountAndDate(String accountNumber, String date) {
        var parsedDate = LocalDate.parse(date); // Will throw DateTimeParseException for invalid format
        Optional<StatementDto> s =
                this.statementService.getStatementDtoByAccountNumberAndStatementDate(accountNumber, parsedDate);
        return s.map(v -> List.of(this.statementApiMapper.toApi(v))).orElseGet(List::of);
    }

    /**
     * OpenAPI paged search implementation. Returns a StatementSummaryPage with BaseStatement content.
     * At least one of accountNumber or date must be provided. If only date is provided, returns an empty page for now.
     */
    public StatementSummaryPage searchPaged(
            String accountNumber, String date, Integer page, Integer size, String sort) {
        boolean hasAccount = accountNumber != null && !accountNumber.isBlank();
        boolean hasDate = date != null && !date.isBlank();

        if (!hasAccount && !hasDate) {
            throw new IllegalArgumentException("At least one of accountNumber or date must be provided");
        }

        int pageNum = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? 50 : Math.max(1, Math.min(100, size));

        StatementSummaryPage result = new StatementSummaryPage();
        result.page(pageNum);
        result.size(pageSize);

        // Stable default sort: uploadedAt desc, then id desc
        Sort defaultSort = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

        if (hasAccount && hasDate) {
            var parsedDate = LocalDate.parse(date); // may throw DateTimeParseException
            Optional<StatementDto> opt =
                    this.statementService.getStatementDtoByAccountNumberAndStatementDate(accountNumber, parsedDate);
            var content = opt.map(dto -> List.of(toBase(dto))).orElseGet(List::of);
            result.setContent(content);
            long total = content.size();
            result.totalElements(total);
            int totalPages = (int) Math.ceil(total / (double) pageSize);
            result.totalPages(totalPages);
            return result;
        }

        if (hasAccount) {
            Page<Statement> statements = this.statementService.getStatementsByAccountNumber(
                    accountNumber, PageRequest.of(pageNum, pageSize, defaultSort));
            var content =
                    statements.map(stmt -> toBase(statementService.toDto(stmt))).getContent();
            result.setContent(content);
            result.totalElements(statements.getTotalElements());
            result.totalPages(statements.getTotalPages());
            return result;
        }

        // Only date provided: currently no repository support for date-only; return empty page
        result.setContent(new ArrayList<>());
        result.totalElements(0L);
        result.totalPages(0);
        return result;
    }

    // Minimal manual mapper for BaseStatement to avoid MapStruct dependency on generated type
    private com.example.statementservice.model.api.BaseStatement toBase(StatementDto dto) {
        var base = new com.example.statementservice.model.api.BaseStatement();
        base.setStatementId(dto.getStatementId());
        base.setAccountNumber(dto.getAccountNumber());
        base.setUploadedAt(dto.getUploadedAt());
        base.setFileSize(dto.getFileSize());
        base.setFileName(dto.getFileName());
        base.setDate(dto.getStatementDate() != null ? dto.getStatementDate().toString() : null);
        return base;
    }
}
