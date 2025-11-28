# GlobalExceptionHandler - Comprehensive Review and Improvement Recommendations

## Executive Summary

The [`GlobalExceptionHandler`](src/main/java/com/example/statementservice/exception/advice/GlobalExceptionHandler.java) provides good RFC 7807 Problem Details support but has **critical security issues, missing exception handlers, and poor logging**.

**Severity: HIGH** - Security vulnerabilities and missing error handling require immediate attention.

---

## Current Architecture Analysis

### Strengths ✅

1. **RFC 7807 Compliance**: Uses Spring's `ProblemDetail` for standardized error responses
2. **Structured Error Codes**: Consistent error code constants
3. **Type URIs**: Proper error type categorization
4. **Centralized Handling**: Single point for exception management

### Critical Weaknesses ⚠️

1. **Security Vulnerability**: Exposes internal error details to clients
2. **Missing Exception Handler**: `StatementNotFoundException` not handled (returns 500 instead of 404)
3. **No Logging**: Zero logging of exceptions for debugging/monitoring
4. **Overly Broad Handlers**: `RuntimeException` catches too much
5. **Information Leakage**: Stack traces and internal messages exposed
6. **No Request Context**: Missing request ID, path, timestamp
7. **Missing Validation Details**: No field-level validation errors

---

## Critical Issues

### 1. **Security Vulnerability: Information Leakage** 🔴 CRITICAL

**Location:** All exception handlers

**Current Implementation:**
```java
@ExceptionHandler(Exception.class)
public ProblemDetail handleGeneric(Exception ex) {
    return createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        TYPE_INTERNAL,
        DEFAULT_INTERNAL_ERROR_MSG,
        ex.getMessage(),  // ⚠️ Exposes internal error details!
        ERROR_CODE_INTERNAL_ERROR);
}
```

**Problem:**
- Line 167: `ex.getMessage()` exposes internal error details
- Stack traces could reveal file paths, database schemas, internal logic
- Could expose sensitive information like connection strings, file paths
- Violates OWASP security guidelines

**Example Exposure:**
```json
{
  "type": "https://api.example.com/errors/internal",
  "title": "Internal server error",
  "detail": "Connection refused: jdbc:postgresql://internal-db.company.com:5432/statements",
  "errorCode": "INTERNAL_ERROR"
}
```

**Impact:** 
- Information disclosure vulnerability
- Aids attackers in reconnaissance
- Violates security best practices

---

### 2. **Missing Exception Handler: StatementNotFoundException** 🔴 HIGH

**Problem:**
- [`StatementNotFoundException`](src/main/java/com/example/statementservice/exception/StatementNotFoundException.java:3) exists but has no handler
- Falls through to `RuntimeException` handler (line 151-159)
- Returns 400 Bad Request instead of 404 Not Found
- Violates REST API conventions

**Current Behavior:**
```java
// Controller throws StatementNotFoundException
throw new StatementNotFoundException("Statement not found: " + id);

// Caught by RuntimeException handler -> 400 Bad Request ❌
// Should be 404 Not Found ✅
```

---

### 3. **No Logging of Exceptions** 🔴 HIGH

**Location:** All handlers

**Problem:**
- Zero logging throughout the entire handler
- No way to debug production issues
- No metrics or monitoring integration
- Cannot track error patterns or rates
- Missing correlation IDs for request tracing

**Impact:**
- Impossible to debug production issues
- No visibility into error rates
- Cannot identify systemic problems
- Poor operational observability

---

### 4. **Overly Broad RuntimeException Handler** 🟡 MEDIUM

**Location:** [`GlobalExceptionHandler.java:151-159`](src/main/java/com/example/statementservice/exception/advice/GlobalExceptionHandler.java:151)

**Problem:**
```java
@ExceptionHandler(RuntimeException.class)
public ProblemDetail handleRuntime(RuntimeException ex) {
    return createProblemDetail(
        HttpStatus.BAD_REQUEST,  // ⚠️ All RuntimeExceptions -> 400
        TYPE_VALIDATION,
        DEFAULT_BAD_REQUEST_MSG,
        ex.getMessage(),
        ERROR_CODE_INVALID_INPUT);
}
```

**Issues:**
- Catches ALL `RuntimeException` subclasses
- Returns 400 for everything (incorrect for 404, 500, etc.)
- Masks specific exceptions that should have different status codes
- Makes debugging harder

---

### 5. **Missing Validation Field Details** 🟡 MEDIUM

**Location:** [`handleValidationExceptions()`](src/main/java/com/example/statementservice/exception/advice/GlobalExceptionHandler.java:66)

**Problem:**
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ProblemDetail handleValidationExceptions(Exception ex) {
    return createProblemDetail(
        HttpStatus.BAD_REQUEST,
        TYPE_VALIDATION,
        "Validation Failed",
        ex.getMessage(),  // ⚠️ Generic message, no field details
        ERROR_CODE_INVALID_INPUT);
}
```

**Missing Information:**
- Which fields failed validation
- What the validation rules were
- What values were provided
- Multiple validation errors in single response

**Expected Response:**
```json
{
  "type": "https://api.example.com/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "errors": [
    {
      "field": "accountNumber",
      "rejectedValue": "123",
      "message": "Account number must be 8-20 characters"
    },
    {
      "field": "statementDate",
      "rejectedValue": "invalid-date",
      "message": "Invalid date format. Expected YYYY-MM-DD"
    }
  ]
}
```

---

### 6. **Missing Request Context** 🟡 MEDIUM

**Problem:**
- No request ID for correlation
- No timestamp
- No request path
- No HTTP method
- Makes debugging distributed systems difficult

---

### 7. **Hardcoded Error Type URIs** 🟡 LOW

**Location:** Lines 40-44

**Problem:**
```java
private static final String TYPE_PREFIX = "https://api.example.com/errors/";
```

- Hardcoded domain
- Not configurable per environment
- Should use application properties

---

## Recommended Solutions

### Solution 1: Fix Security Vulnerability - Sanitize Error Messages

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${app.error.include-details:false}")
    private boolean includeErrorDetails;

    @Value("${app.error.include-stacktrace:false}")
    private boolean includeStackTrace;

    private ProblemDetail createProblemDetail(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            String errorCode,
            Exception ex) {
        
        // Sanitize detail message for production
        String sanitizedDetail = sanitizeErrorDetail(detail, status, ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            status, sanitizedDetail);
        problemDetail.setType(type);
        problemDetail.setTitle(title);
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now());
        
        // Only include stack trace in development
        if (includeStackTrace && ex != null) {
            problemDetail.setProperty("stackTrace", 
                Arrays.stream(ex.getStackTrace())
                    .limit(5)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList()));
        }
        
        return problemDetail;
    }

    private String sanitizeErrorDetail(String detail, HttpStatus status, Exception ex) {
        // For 5xx errors, never expose internal details in production
        if (status.is5xxServerError() && !includeErrorDetails) {
            return "An internal error occurred. Please contact support with the request ID.";
        }
        
        // For 4xx errors, sanitize but allow some detail
        if (status.is4xxClientError()) {
            // Remove file paths, connection strings, etc.
            return sanitizeClientErrorDetail(detail);
        }
        
        return detail;
    }

    private String sanitizeClientErrorDetail(String detail) {
        if (detail == null) return null;
        
        // Remove file paths
        detail = detail.replaceAll("/[\\w/.-]+", "[path]");
        // Remove connection strings
        detail = detail.replaceAll("jdbc:[\\w:/@.-]+", "[connection]");
        // Remove IP addresses
        detail = detail.replaceAll("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", "[ip]");
        
        return detail;
    }
}
```

**Configuration:**
```yaml
app:
  error:
    include-details: false  # true only in dev
    include-stacktrace: false  # true only in dev
```

---

### Solution 2: Add Missing StatementNotFoundException Handler

```java
@ExceptionHandler(StatementNotFoundException.class)
public ProblemDetail handleStatementNotFound(
        StatementNotFoundException ex,
        HttpServletRequest request) {
    
    log.warn("Statement not found - path: {}, message: {}", 
             request.getRequestURI(), ex.getMessage());
    
    return createProblemDetail(
        HttpStatus.NOT_FOUND,
        URI.create(TYPE_PREFIX + "not-found"),
        "Statement Not Found",
        "The requested statement could not be found",
        "STATEMENT_NOT_FOUND",
        ex);
}
```

---

### Solution 3: Add Comprehensive Logging

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        
        String requestId = generateRequestId();
        
        // Log with full context
        log.error("Unhandled exception - requestId: {}, path: {}, method: {}, error: {}",
                  requestId,
                  request.getRequestURI(),
                  request.getMethod(),
                  ex.getMessage(),
                  ex);  // Full stack trace in logs only
        
        // Track metrics
        meterRegistry.counter("exception.unhandled",
            "type", ex.getClass().getSimpleName(),
            "path", request.getRequestURI()
        ).increment();
        
        ProblemDetail problem = createProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            TYPE_INTERNAL,
            "Internal Server Error",
            "An unexpected error occurred",
            ERROR_CODE_INTERNAL_ERROR,
            ex);
        
        problem.setProperty("requestId", requestId);
        problem.setProperty("path", request.getRequestURI());
        
        return problem;
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}
```

---

### Solution 4: Add Detailed Validation Error Handling

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ProblemDetail handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {
    
    log.warn("Validation failed - path: {}, errors: {}", 
             request.getRequestURI(), ex.getBindingResult().getErrorCount());
    
    ProblemDetail problemDetail = createProblemDetail(
        HttpStatus.BAD_REQUEST,
        TYPE_VALIDATION,
        "Validation Failed",
        "One or more fields failed validation",
        ERROR_CODE_INVALID_INPUT,
        ex);
    
    // Add field-level validation errors
    List<Map<String, Object>> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> Map.of(
            "field", error.getField(),
            "rejectedValue", error.getRejectedValue() != null ? 
                error.getRejectedValue() : "null",
            "message", error.getDefaultMessage() != null ? 
                error.getDefaultMessage() : "Invalid value"
        ))
        .collect(Collectors.toList());
    
    problemDetail.setProperty("errors", errors);
    problemDetail.setProperty("errorCount", errors.size());
    
    return problemDetail;
}

@ExceptionHandler(ConstraintViolationException.class)
public ProblemDetail handleConstraintViolation(
        ConstraintViolationException ex,
        HttpServletRequest request) {
    
    log.warn("Constraint violation - path: {}, violations: {}", 
             request.getRequestURI(), ex.getConstraintViolations().size());
    
    ProblemDetail problemDetail = createProblemDetail(
        HttpStatus.BAD_REQUEST,
        TYPE_VALIDATION,
        "Validation Failed",
        "One or more constraints were violated",
        ERROR_CODE_INVALID_INPUT,
        ex);
    
    List<Map<String, Object>> violations = ex.getConstraintViolations()
        .stream()
        .map(violation -> Map.of(
            "property", violation.getPropertyPath().toString(),
            "invalidValue", violation.getInvalidValue() != null ? 
                violation.getInvalidValue().toString() : "null",
            "message", violation.getMessage()
        ))
        .collect(Collectors.toList());
    
    problemDetail.setProperty("violations", violations);
    
    return problemDetail;
}
```

---

### Solution 5: Remove Overly Broad RuntimeException Handler

```java
// ❌ REMOVE THIS - Too broad
@ExceptionHandler(RuntimeException.class)
public ProblemDetail handleRuntime(RuntimeException ex) {
    // This catches everything!
}

// ✅ ADD SPECIFIC HANDLERS INSTEAD
@ExceptionHandler(IllegalArgumentException.class)
public ProblemDetail handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request) {
    
    log.warn("Illegal argument - path: {}, message: {}", 
             request.getRequestURI(), ex.getMessage());
    
    return createProblemDetail(
        HttpStatus.BAD_REQUEST,
        TYPE_VALIDATION,
        "Invalid Argument",
        sanitizeErrorDetail(ex.getMessage(), HttpStatus.BAD_REQUEST, ex),
        ERROR_CODE_INVALID_INPUT,
        ex);
}

@ExceptionHandler(IllegalStateException.class)
public ProblemDetail handleIllegalState(
        IllegalStateException ex,
        HttpServletRequest request) {
    
    log.error("Illegal state - path: {}, message: {}", 
              request.getRequestURI(), ex.getMessage(), ex);
    
    return createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        TYPE_INTERNAL,
        "Invalid State",
        "The operation cannot be completed in the current state",
        "ILLEGAL_STATE",
        ex);
}
```

---

### Solution 6: Add Request Context with MDC

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(
            Exception ex,
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        
        // Use provided request ID or generate new one
        String correlationId = requestId != null ? requestId : UUID.randomUUID().toString();
        
        // Add to MDC for logging
        MDC.put("requestId", correlationId);
        MDC.put("path", request.getRequestURI());
        MDC.put("method", request.getMethod());
        
        try {
            log.error("Exception occurred", ex);
            
            ProblemDetail problem = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                TYPE_INTERNAL,
                "Internal Server Error",
                "An unexpected error occurred",
                ERROR_CODE_INTERNAL_ERROR,
                ex);
            
            // Add request context to response
            problem.setProperty("requestId", correlationId);
            problem.setProperty("path", request.getRequestURI());
            problem.setProperty("method", request.getMethod());
            problem.setProperty("timestamp", Instant.now());
            
            return problem;
        } finally {
            MDC.clear();
        }
    }
}
```

---

### Solution 7: Make Error Type URIs Configurable

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${app.error.type-prefix:https://api.example.com/errors/}")
    private String typePrefix;

    private URI createTypeUri(String errorType) {
        return URI.create(typePrefix + errorType);
    }

    @ExceptionHandler(InvalidDateException.class)
    public ProblemDetail handleInvalidDate(InvalidDateException ex) {
        return createProblemDetail(
            HttpStatus.BAD_REQUEST,
            createTypeUri("validation"),  // ✅ Configurable
            "Invalid Date Format",
            ex.getMessage(),
            ERROR_CODE_INVALID_DATE,
            ex);
    }
}
```

**Configuration:**
```yaml
app:
  error:
    type-prefix: https://${app.domain}/api/errors/
```

---

## Complete Refactored GlobalExceptionHandler

```java
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;

    @Value("${app.error.type-prefix:https://api.example.com/errors/}")
    private String typePrefix;

    @Value("${app.error.include-details:false}")
    private boolean includeErrorDetails;

    @Value("${app.error.include-stacktrace:false}")
    private boolean includeStackTrace;

    // Error codes
    private static final String ERROR_CODE_VALIDATION = "VALIDATION_ERROR";
    private static final String ERROR_CODE_NOT_FOUND = "NOT_FOUND";
    private static final String ERROR_CODE_INTERNAL = "INTERNAL_ERROR";

    @ExceptionHandler(StatementNotFoundException.class)
    public ProblemDetail handleStatementNotFound(
            StatementNotFoundException ex,
            HttpServletRequest request) {
        
        String requestId = getOrGenerateRequestId(request);
        
        log.warn("Statement not found - requestId: {}, path: {}, message: {}", 
                 requestId, request.getRequestURI(), ex.getMessage());
        
        meterRegistry.counter("exception.statement_not_found").increment();
        
        return buildProblemDetail(
            HttpStatus.NOT_FOUND,
            "not-found",
            "Statement Not Found",
            "The requested statement could not be found",
            ERROR_CODE_NOT_FOUND,
            request,
            requestId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        String requestId = getOrGenerateRequestId(request);
        
        log.warn("Validation failed - requestId: {}, path: {}, errorCount: {}", 
                 requestId, request.getRequestURI(), 
                 ex.getBindingResult().getErrorCount());
        
        meterRegistry.counter("exception.validation").increment();
        
        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            "validation",
            "Validation Failed",
            "One or more fields failed validation",
            ERROR_CODE_VALIDATION,
            request,
            requestId);
        
        // Add field errors
        List<Map<String, Object>> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "rejectedValue", Objects.toString(error.getRejectedValue(), "null"),
                "message", Objects.toString(error.getDefaultMessage(), "Invalid value")
            ))
            .collect(Collectors.toList());
        
        problem.setProperty("errors", errors);
        problem.setProperty("errorCount", errors.size());
        
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        String requestId = getOrGenerateRequestId(request);
        
        log.error("Unhandled exception - requestId: {}, path: {}, type: {}", 
                  requestId, request.getRequestURI(), 
                  ex.getClass().getSimpleName(), ex);
        
        meterRegistry.counter("exception.unhandled",
            "type", ex.getClass().getSimpleName()
        ).increment();
        
        return buildProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal",
            "Internal Server Error",
            includeErrorDetails ? ex.getMessage() : 
                "An unexpected error occurred. Please contact support.",
            ERROR_CODE_INTERNAL,
            request,
            requestId);
    }

    private ProblemDetail buildProblemDetail(
            HttpStatus status,
            String typeSegment,
            String title,
            String detail,
            String errorCode,
            HttpServletRequest request,
            String requestId) {
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(typePrefix + typeSegment));
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", requestId);
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("timestamp", Instant.now());
        
        return problem;
    }

    private String getOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
```

---

## Testing Recommendations

```java
@WebMvcTest(controllers = StatementsController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatementQueryService statementQueryService;

    @Test
    void shouldReturn404ForStatementNotFound() throws Exception {
        when(statementQueryService.getSummaryById(any()))
            .thenThrow(new StatementNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/statements/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(containsString("not-found")))
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/statements/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(containsString("validation")))
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void shouldNotExposeInternalDetailsIn500Error() throws Exception {
        when(statementQueryService.getSummaryById(any()))
            .thenThrow(new RuntimeException("Database connection failed: jdbc:postgresql://internal-db:5432"));

        mockMvc.perform(get("/api/v1/statements/{id}", UUID.randomUUID()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value(not(containsString("jdbc"))))
            .andExpect(jsonPath("$.detail").value(not(containsString("internal-db"))));
    }
}
```

---

## Security Checklist

- ✅ Sanitize all error messages before sending to client
- ✅ Never expose stack traces in production
- ✅ Never expose file paths, connection strings, or internal IPs
- ✅ Log full details server-side for debugging
- ✅ Use generic messages for 500 errors
- ✅ Include request IDs for correlation
- ✅ Configure detail level per environment
- ✅ Monitor exception rates for anomalies

---

## Conclusion

The GlobalExceptionHandler requires immediate security fixes and improvements:

- 🔴 **CRITICAL**: Fix information leakage vulnerability
- 🔴 **HIGH**: Add StatementNotFoundException handler (404 instead of 400)
- 🔴 **HIGH**: Add comprehensive logging with request context
- 🟡 **MEDIUM**: Remove overly broad RuntimeException handler
- 🟡 **MEDIUM**: Add detailed validation error responses
- 🟡 **MEDIUM**: Add request context (ID, path, timestamp)
- 🟢 **LOW**: Make error type URIs configurable

**Priority: CRITICAL - Security vulnerability must be fixed immediately**

---

## References

- Current Implementation: [`GlobalExceptionHandler.java`](src/main/java/com/example/statementservice/exception/advice/GlobalExceptionHandler.java)
- RFC 7807: Problem Details for HTTP APIs
- OWASP: Error Handling Cheat Sheet
- Spring Boot: Error Handling Best Practices