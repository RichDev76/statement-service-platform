package com.example.statementservice.exception.advice;

import static com.example.statementservice.util.CommonUtil.buildProblemDetailTypeURI;

import com.example.statementservice.exception.DecryptionFailedException;
import com.example.statementservice.exception.DigestComputationException;
import com.example.statementservice.exception.DigestMismatchException;
import com.example.statementservice.exception.DownloadFileMissingException;
import com.example.statementservice.exception.DownloadInvalidSignatureException;
import com.example.statementservice.exception.DownloadLinkExpiredException;
import com.example.statementservice.exception.InvalidAccountNumberException;
import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.exception.InvalidMessageDigestException;
import com.example.statementservice.exception.MissingFileException;
import com.example.statementservice.exception.PdfValidationException;
import com.example.statementservice.exception.SignatureException;
import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.exception.StatementUploadException;
import com.example.statementservice.exception.UnsupportedContentTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Error Code Constants
    private static final String ERROR_CODE_INVALID_INPUT = "INVALID_INPUT";
    private static final String ERROR_CODE_INVALID_MESSAGE_DIGEST = "INVALID_MESSAGE_DIGEST";
    private static final String ERROR_CODE_MISSING_FILE = "MISSING_FILE";
    private static final String ERROR_CODE_INVALID_ACCOUNT_NUMBER = "INVALID_ACCOUNT_NUMBER";
    private static final String ERROR_CODE_INVALID_DATE = "INVALID_DATE";
    private static final String ERROR_CODE_DIGEST_MISMATCH = "DIGEST_MISMATCH";
    private static final String ERROR_CODE_DIGEST_ERROR = "DIGEST_ERROR";
    private static final String ERROR_CODE_UPLOAD_FAILED = "STATEMENT_UPLOAD_FAILED";
    private static final String ERROR_CODE_UNSUPPORTED_MEDIA = "UNSUPPORTED_MEDIA_TYPE";
    private static final String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String ERROR_CODE_STATEMENT_NOT_FOUND = "STATEMENT_NOT_FOUND";
    private static final String ERROR_CODE_INVALID_SIGNATURE = "INVALID_SIGNATURE";
    private static final String ERROR_CODE_LINK_EXPIRED = "LINK_EXPIRED_OR_USED";
    private static final String ERROR_CODE_FILE_MISSING = "FILE_MISSING";
    private static final String ERROR_CODE_DECRYPTION_FAILED = "DECRYPTION_FAILED";

    // Error Type URIs
    private static final String TYPE_PREFIX = "/errors/";
    private static final String TYPE_VALIDATION = TYPE_PREFIX + "validation";
    private static final String TYPE_UPLOAD = TYPE_PREFIX + "upload";
    private static final String TYPE_MEDIA_TYPE = TYPE_PREFIX + "media-type";
    private static final String TYPE_INTERNAL = TYPE_PREFIX + "internal";
    private static final String TYPE_STATEMENT = TYPE_PREFIX + "statement";
    private static final String TYPE_DOWNLOAD = TYPE_PREFIX + "download";

    // Default Messages
    private static final String DEFAULT_BAD_REQUEST_MSG = "Bad request";
    private static final String DEFAULT_INTERNAL_ERROR_MSG = "Internal server error";

    private ProblemDetail createProblemDetail(
            HttpStatus status, URI type, String title, String detail, String errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : title);
        problemDetail.setType(type);
        problemDetail.setTitle(title);
        problemDetail.setProperty("errorCode", errorCode);
        return problemDetail;
    }

    @ExceptionHandler(StatementNotFoundException.class)
    public ProblemDetail handleStatementNotFound(StatementNotFoundException ex, HttpServletRequest request) {

        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                buildProblemDetailTypeURI(request, TYPE_STATEMENT),
                "Not Found",
                ex.getMessage(),
                ERROR_CODE_STATEMENT_NOT_FOUND);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        PdfValidationException.class
    })
    public ProblemDetail handleValidationExceptions(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Validation Failed",
                ex.getMessage(),
                ERROR_CODE_INVALID_INPUT);
    }

    @ExceptionHandler(InvalidMessageDigestException.class)
    public ProblemDetail handleInvalidMessageDigest(InvalidMessageDigestException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Invalid Message Digest",
                ex.getMessage(),
                ERROR_CODE_INVALID_MESSAGE_DIGEST);
    }

    @ExceptionHandler(MissingFileException.class)
    public ProblemDetail handleMissingFile(MissingFileException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Missing File",
                ex.getMessage(),
                ERROR_CODE_MISSING_FILE);
    }

    @ExceptionHandler(InvalidAccountNumberException.class)
    public ProblemDetail handleInvalidAccountNumber(InvalidAccountNumberException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Invalid Account Number",
                ex.getMessage(),
                ERROR_CODE_INVALID_ACCOUNT_NUMBER);
    }

    @ExceptionHandler(InvalidDateException.class)
    public ProblemDetail handleInvalidDate(InvalidDateException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Invalid Date Format",
                ex.getMessage(),
                ERROR_CODE_INVALID_DATE);
    }

    @ExceptionHandler(DigestMismatchException.class)
    public ProblemDetail handleDigestMismatch(DigestMismatchException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Digest Mismatch",
                ex.getMessage(),
                ERROR_CODE_DIGEST_MISMATCH);
    }

    @ExceptionHandler(DigestComputationException.class)
    public ProblemDetail handleDigestComputation(DigestComputationException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                "Digest Computation Failed",
                ex.getMessage(),
                ERROR_CODE_DIGEST_ERROR);
    }

    @ExceptionHandler(StatementUploadException.class)
    public ProblemDetail handleUploadFailure(StatementUploadException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_UPLOAD),
                "Statement Upload Failed",
                ex.getMessage(),
                ERROR_CODE_UPLOAD_FAILED);
    }

    @ExceptionHandler(DownloadInvalidSignatureException.class)
    public ProblemDetail handleInvalidSignature(DownloadInvalidSignatureException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.FORBIDDEN,
                buildProblemDetailTypeURI(request, TYPE_DOWNLOAD),
                "Invalid Signature",
                ex.getMessage(),
                ERROR_CODE_INVALID_SIGNATURE);
    }

    @ExceptionHandler(DownloadLinkExpiredException.class)
    public ProblemDetail handleLinkExpired(DownloadLinkExpiredException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                buildProblemDetailTypeURI(request, TYPE_DOWNLOAD),
                "Link Expired or Used",
                ex.getMessage(),
                ERROR_CODE_LINK_EXPIRED);
    }

    @ExceptionHandler(DownloadFileMissingException.class)
    public ProblemDetail handleFileMissing(DownloadFileMissingException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                buildProblemDetailTypeURI(request, TYPE_DOWNLOAD),
                "File Missing",
                ex.getMessage(),
                ERROR_CODE_FILE_MISSING);
    }

    @ExceptionHandler(DecryptionFailedException.class)
    public ProblemDetail handleDecryptionFailed(DecryptionFailedException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_DOWNLOAD),
                "Decryption Failed",
                ex.getMessage(),
                ERROR_CODE_DECRYPTION_FAILED);
    }

    @ExceptionHandler({UnsupportedContentTypeException.class, HttpMediaTypeNotSupportedException.class})
    public ProblemDetail handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                buildProblemDetailTypeURI(request, TYPE_MEDIA_TYPE),
                "Unsupported Media Type",
                ex.getMessage(),
                ERROR_CODE_UNSUPPORTED_MEDIA);
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                buildProblemDetailTypeURI(request, TYPE_VALIDATION),
                DEFAULT_BAD_REQUEST_MSG,
                ex.getMessage(),
                ERROR_CODE_INVALID_INPUT);
    }

    @ExceptionHandler({Exception.class, SignatureException.class})
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                buildProblemDetailTypeURI(request, TYPE_INTERNAL),
                DEFAULT_INTERNAL_ERROR_MSG,
                ex.getMessage(),
                ERROR_CODE_INTERNAL_ERROR);
    }
}
