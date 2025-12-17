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

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;

    public Optional<StatementSummary> getStatementSummaryWithSignedDownloadLinkById(UUID statementId) {
        String performedBy = getPerformedBy();

        try {
            StatementDto dto = this.statementService.getStatementDtoById(statementId);
            String accountNumber = dto.getAccountNumber();

            try {
                var signedLinkBasePath = this.signedLinkService.getFilesBaseUrl(dto.getFileName());
                var signedLink = this.signedLinkService.createSignedLink(
                        dto.getStatementId(), true, performedBy, signedLinkBasePath);
                URI signedDownloadLink = signedLinkService.buildSignedDownloadLink(signedLink, signedLinkBasePath);
                dto.setDownloadLink(signedDownloadLink);

                auditHelper.recordLinkGenerated(statementId, accountNumber, signedLink.getId(), performedBy);

            } catch (Exception linkException) {
                auditHelper.recordLinkGenerationFailed(statementId, accountNumber, performedBy, linkException);
            }

            return Optional.of(statementApiMapper.toApi(dto));

        } catch (StatementNotFoundException e) {
            auditHelper.recordStatementNotFound(statementId, performedBy);
            return Optional.empty();
        } catch (Exception e) {
            auditHelper.recordUnexpectedError(statementId, null, performedBy, e);
            throw e;
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
        int effectiveLimit = (limit != null) ? limit : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null) ? offset : DEFAULT_OFFSET;

        try {
            var statements = this.statementService.getStatementsDtoByAccountNumber(accountNumber);
            int fromIndex = Math.min(effectiveOffset, statements.size());
            int toIndex = Math.min(fromIndex + effectiveLimit, statements.size());
            var page = statements.subList(fromIndex, toIndex);
            return this.statementApiMapper.toApis(page);
        } catch (StatementNotFoundException e) {
            return new ArrayList<>();
        }
    }

    public List<StatementSummary> searchByAccountAndDate(String accountNumber, String date) {
        var parsedDate = LocalDate.parse(date);
        Optional<StatementDto> s =
                this.statementService.getStatementDtoByAccountNumberAndStatementDate(accountNumber, parsedDate);
        return s.map(v -> List.of(this.statementApiMapper.toApi(v))).orElseGet(List::of);
    }

    public StatementSummaryPage searchPaged(
            String accountNumber, String date, Integer page, Integer size, String sort) {

        int effectivePage = (page != null) ? page : DEFAULT_PAGE;
        int effectiveSize = (size != null) ? size : DEFAULT_SIZE;

        var result = new StatementSummaryPage();
        result.page(effectivePage);
        result.size(effectiveSize);

        var defaultSort = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

        // Both account and date provided
        if (accountNumber != null && date != null) {
            var parsedDate = LocalDate.parse(date);
            Optional<StatementDto> opt =
                    this.statementService.getStatementDtoByAccountNumberAndStatementDate(accountNumber, parsedDate);
            var content = opt.map(dto -> List.of(toBase(dto))).orElseGet(List::of);
            long total = content.size();
            int totalPages = (int) Math.ceil(total / (double) effectiveSize);
            result.setContent(content);
            result.totalElements(total);
            result.totalPages(totalPages);
            return result;
        }

        // Account only
        if (accountNumber != null) {
            Page<Statement> statements = this.statementService.getStatementsByAccountNumber(
                    accountNumber, PageRequest.of(effectivePage, effectiveSize, defaultSort));
            var content =
                    statements.map(stmt -> toBase(statementService.toDto(stmt))).getContent();
            result.setContent(content);
            result.totalElements(statements.getTotalElements());
            result.totalPages(statements.getTotalPages());
            return result;
        }

        // Date only (not currently supported by business logic)
        result.setContent(new ArrayList<>());
        result.totalElements(0L);
        result.totalPages(0);
        return result;
    }

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
