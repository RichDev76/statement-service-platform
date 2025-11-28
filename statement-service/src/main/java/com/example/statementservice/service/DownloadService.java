package com.example.statementservice.service;

import com.example.statementservice.enums.AuditAction;
import com.example.statementservice.enums.DownloadFailureReason;
import com.example.statementservice.enums.DownloadOutcome;
import com.example.statementservice.enums.ValidationFailureReason;
import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.repository.StatementRepository;
import com.example.statementservice.util.LinkValidationResult;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadService {

    private static final String AUDIT_KEY_IP = "ip";
    private static final String AUDIT_KEY_USER_AGENT = "userAgent";
    private static final String AUDIT_KEY_TOKEN = "token";
    private static final String AUDIT_KEY_REASON = "reason";
    private static final String AUDIT_KEY_ERROR = "error";
    private static final String AUDIT_UNKNOWN = "unknown";

    private final SignedLinkService signedLinkService;
    private final StatementRepository statementRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    /**
     * Detailed validation and streaming method to support controller status-code mapping per OpenAPI.
     * Currently binds only to signature token; fileName/expires are part of the signed token payload
     * validated in SignedLinkService/SignatureUtil. If future coupling is needed, extend validation here.
     */
    public DownloadStreamResult validateAndStreamDetailed(
            String token, String clientIp, String userAgent, String performedBy) {
        log.debug("Download request (detailed) - token: {}, ip: {}, user: {}", maskToken(token), clientIp, performedBy);

        // Step 1: Validate link
        LinkValidationResult result = signedLinkService.validateAndConsume(token);
        if (!result.isValid()) {
            String reason = getReason(result);
            UUID statementId = result.getLink() != null ? result.getLink().getStatementId() : null;
            UUID linkId = result.getLink() != null ? result.getLink().getId() : null;
            String accountNumber = fetchAccountNumber(statementId);
            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statementId,
                    accountNumber,
                    linkId,
                    performedBy,
                    getUserAuditDetails(token, clientIp, userAgent, reason));

            DownloadOutcome outcome = (result.getFailureReason() == ValidationFailureReason.USED
                            || result.getFailureReason() == ValidationFailureReason.EXPIRED)
                    ? DownloadOutcome.LINK_EXPIRED_OR_USED
                    : DownloadOutcome
                            .INVALID_SIGNATURE; // NOT_FOUND maps to invalid signature per spec 403 vs 404 distinction
            // downstream
            if (result.getFailureReason() == ValidationFailureReason.NOT_FOUND) {
                // Treat as 404 (not found) in controller
                outcome = DownloadOutcome.STATEMENT_NOT_FOUND;
            }
            return new DownloadStreamResult(outcome, Optional.empty());
        }

        // Step 2: Fetch statement
        SignedLink link = result.getLink();
        Optional<Statement> statementOpt = statementRepository.findById(link.getStatementId());
        if (statementOpt.isEmpty()) {
            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    link.getStatementId(),
                    null,
                    link.getId(),
                    performedBy,
                    getUserAuditDetails(
                            token, clientIp, userAgent, DownloadFailureReason.STATEMENT_NOT_FOUND.getValue()));
            return new DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());
        }

        // Step 3: Verify file exists
        Statement statement = statementOpt.get();
        File storedFile = new File(statement.getFilePath());
        if (!storedFile.exists()) {
            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    performedBy,
                    getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.FILE_MISSING.getValue()));
            return new DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());
        }

        // Step 4: Decrypt and stream
        try {
            InputStream decrypted = encryptionService.decryptFileToStream(storedFile);
            try {
                auditService.record(
                        AuditAction.DOWNLOAD_SUCCESS.getValue(),
                        statement.getId(),
                        statement.getAccountNumber(),
                        link.getId(),
                        performedBy,
                        getUserAuditDetails(token, clientIp, userAgent, null));
            } catch (Exception auditEx) {
                log.warn("Failed to record download audit", auditEx);
            }
            return new DownloadStreamResult(DownloadOutcome.OK, Optional.of(decrypted));
        } catch (Exception e) {
            log.error("Decryption failed - statementId: {}, error: {}", statement.getId(), e.getMessage(), e);
            var errorAuditDetails = new HashMap<>(getUserAuditDetails(
                    token, clientIp, userAgent, DownloadFailureReason.DECRYPTION_FAILED.getValue()));
            errorAuditDetails.put(AUDIT_KEY_ERROR, e.getMessage());
            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    performedBy,
                    errorAuditDetails);
            return new DownloadStreamResult(DownloadOutcome.DECRYPTION_FAILED, Optional.empty());
        }
    }

    public record DownloadStreamResult(DownloadOutcome outcome, Optional<InputStream> stream) {}

    private String getReason(LinkValidationResult result) {
        String reason;
        switch (result.getFailureReason()) {
            case ValidationFailureReason.USED -> reason = DownloadFailureReason.USED.getValue();
            case ValidationFailureReason.EXPIRED -> reason = DownloadFailureReason.EXPIRED.getValue();
            default -> reason = DownloadFailureReason.INVALID.getValue();
        }
        return reason;
    }

    private Map<String, Object> getUserAuditDetails(String token, String clientIp, String userAgent, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put(AUDIT_KEY_IP, clientIp != null ? clientIp : AUDIT_UNKNOWN);
        details.put(AUDIT_KEY_USER_AGENT, userAgent != null ? userAgent : AUDIT_UNKNOWN);
        details.put(AUDIT_KEY_TOKEN, maskToken(token));
        if (reason != null) {
            details.put(AUDIT_KEY_REASON, reason);
        }
        return details;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private Optional<InputStream> handleInvalidLink(
            LinkValidationResult result, String token, String clientIp, String userAgent, String performedBy) {
        String reason = getReason(result);
        UUID statementId = result.getLink() != null ? result.getLink().getStatementId() : null;
        UUID linkId = result.getLink() != null ? result.getLink().getId() : null;

        // Optionally fetch account number for better audit context
        String accountNumber = fetchAccountNumber(statementId);

        log.warn("Link validation failed - reason: {}, statementId: {}", reason, statementId);
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                statementId,
                accountNumber,
                linkId,
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, reason));

        return Optional.empty();
    }

    private Optional<InputStream> handleMissingStatement(
            SignedLink link, String token, String clientIp, String userAgent, String performedBy) {
        log.error("Statement not found for link - statementId: {}", link.getStatementId());
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                link.getStatementId(),
                null,
                link.getId(),
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.STATEMENT_NOT_FOUND.getValue()));

        return Optional.empty();
    }

    private Optional<InputStream> handleMissingFile(
            Statement statement, SignedLink link, String token, String clientIp, String userAgent, String performedBy) {
        log.error("File not found - path: {}, statementId: {}", statement.getFilePath(), statement.getId());
        auditService.record(
                AuditAction.DOWNLOAD_FAILED.getValue(),
                statement.getId(),
                statement.getAccountNumber(),
                link.getId(),
                performedBy,
                getUserAuditDetails(token, clientIp, userAgent, DownloadFailureReason.FILE_MISSING.getValue()));

        return Optional.empty();
    }

    private Optional<InputStream> decryptAndStream(
            Statement statement,
            SignedLink link,
            File storedFile,
            String token,
            String clientIp,
            String userAgent,
            String performedBy) {
        try {
            InputStream decrypted = encryptionService.decryptFileToStream(storedFile);

            log.info(
                    "Download successful - statementId: {}, account: {}",
                    statement.getId(),
                    statement.getAccountNumber());

            try {
                auditService.record(
                        AuditAction.DOWNLOAD_SUCCESS.getValue(),
                        statement.getId(),
                        statement.getAccountNumber(),
                        link.getId(),
                        performedBy,
                        getUserAuditDetails(token, clientIp, userAgent, null));
            } catch (Exception auditEx) {
                // Log but don't fail the download
                log.warn("Failed to record download audit", auditEx);
            }
            return Optional.of(decrypted);
        } catch (Exception e) {
            log.error("Decryption failed - statementId: {}, error: {}", statement.getId(), e.getMessage(), e);

            var errorAuditDetails = new HashMap<>(getUserAuditDetails(
                    token, clientIp, userAgent, DownloadFailureReason.DECRYPTION_FAILED.getValue()));
            errorAuditDetails.put(AUDIT_KEY_ERROR, e.getMessage());

            auditService.record(
                    AuditAction.DOWNLOAD_FAILED.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    performedBy,
                    errorAuditDetails);

            return Optional.empty();
        }
    }

    private String fetchAccountNumber(UUID statementId) {
        if (statementId == null) return null;
        return statementRepository
                .findById(statementId)
                .map(Statement::getAccountNumber)
                .orElse(null);
    }
}
