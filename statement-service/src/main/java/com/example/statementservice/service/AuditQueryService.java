package com.example.statementservice.service;

import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.mapper.AuditApiMapper;
import com.example.statementservice.mapper.AuditLogEntityMapper;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.model.dto.AuditLogDto;
import com.example.statementservice.model.entity.AuditLog;
import com.example.statementservice.repository.AuditLogRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogEntityMapper auditLogEntityMapper;
    private final AuditApiMapper auditApiMapper;

    /**
     * Retrieves audit logs with database-level filtering and pagination.
     *
     * @param accountNumber Optional account number filter
     * @param startDate Optional start date (YYYY-MM-DD format, inclusive)
     * @param endDate Optional end date (YYYY-MM-DD format, inclusive)
     * @param page Page number (0-indexed, default: 0)
     * @param size Page size (1-100, default: 50)
     * @return Paginated audit log results
     * @throws InvalidDateException if date format is invalid or range is illogical
     */
    public AuditLogPage getFilteredAuditLogs(
            String accountNumber, String startDate, String endDate, Integer page, Integer size) {

        log.debug(
                "Querying audit logs: account={}, start={}, end={}, page={}, size={}",
                accountNumber,
                startDate,
                endDate,
                page,
                size);

        // Validate and parse dates
        OffsetDateTime startDateTime = parseDate(startDate, false);
        OffsetDateTime endDateTime = parseDate(endDate, true);
        validateDateRange(startDateTime, endDateTime);

        // Validate and normalize pagination parameters
        int pageNum = normalizePageNumber(page);
        int pageSize = normalizePageSize(size);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "performedAt"));

        // Execute database query with filtering and pagination
        Page<AuditLog> auditLogPage = auditLogRepository.findFilteredAuditLogs(
                normalizeAccountNumber(accountNumber), startDateTime, endDateTime, pageable);

        // Map to DTOs and API response
        List<AuditLogDto> auditLogDtos = auditLogEntityMapper.toDtos(auditLogPage.getContent());
        AuditLogPage apiPage = auditApiMapper.toPage(auditLogDtos);

        // Set pagination metadata
        apiPage.page(pageNum);
        apiPage.size(pageSize);
        apiPage.totalElements(auditLogPage.getTotalElements());
        apiPage.totalPages(auditLogPage.getTotalPages());

        log.debug(
                "Retrieved {} audit logs (page {} of {})", auditLogDtos.size(), pageNum, auditLogPage.getTotalPages());

        return apiPage;
    }

    private OffsetDateTime parseDate(String dateString, boolean isEndOfDay) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateString.trim());
            if (isEndOfDay) {
                return date.atTime(23, 59, 59, 999999999)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toOffsetDateTime();
            } else {
                return date.atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime();
            }
        } catch (DateTimeParseException e) {
            String dateType = isEndOfDay ? "end" : "start";
            throw new InvalidDateException(
                    "Invalid " + dateType + " date format. Expected YYYY-MM-DD, got: " + dateString, e);
        }
    }

    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidDateException("Start date must be before or equal to end date");
        }
    }

    private String normalizeAccountNumber(String accountNumber) {
        return (accountNumber == null || accountNumber.isBlank()) ? null : accountNumber.trim();
    }

    private int normalizePageNumber(Integer page) {
        return page == null ? DEFAULT_PAGE : Math.max(0, page);
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }
}
