# DownloadService - Review and Improvement Recommendations

## Executive Summary

The [`DownloadService`](src/main/java/com/example/statementservice/service/DownloadService.java) has good audit logging and error handling but suffers from **resource leaks, code duplication, and performance issues**.

**Severity: MEDIUM** - Requires refactoring before production scale.

---

## Critical Issues

### 1. Resource Leak in InputStream Management ⚠️ HIGH

**Location:** [`DownloadService.java:257-303`](src/main/java/com/example/statementservice/service/DownloadService.java:257)

**Problem:**
- InputStream returned but not guaranteed to be closed
- [`EncryptionService.decryptFileToStream()`](src/main/java/com/example/statementservice/service/EncryptionService.java:59) opens FileInputStream without try-with-resources
- If audit logging fails after stream creation, stream leaks
- Caller responsible for closing but not enforced

**Impact:** File descriptor exhaustion, memory leaks under load

**Solution:** Implement `AutoCloseable` result wrapper
```java
@Getter
public class DownloadResult implements AutoCloseable {
    private final DownloadOutcome outcome;
    private final InputStream stream;
    
    @Override
    public void close() {
        if (stream != null) {
            try { stream.close(); } 
            catch (IOException e) { log.warn("Failed to close stream", e); }
        }
    }
}
```

---

### 2. Code Duplication (90%) ⚠️ MEDIUM

**Locations:**
- [`validateAndStream()`](src/main/java/com/example/statementservice/service/DownloadService.java:38) - lines 38-65
- [`validateAndStreamDetailed()`](src/main/java/com/example/statementservice/service/DownloadService.java:90) - lines 90-180

**Problem:** Nearly identical logic, different return types, maintenance burden

**Solution:** Consolidate into single method with proper abstraction

---

### 3. Inefficient Database Access ⚠️ MEDIUM

**Location:** [`fetchAccountNumber()`](src/main/java/com/example/statementservice/service/DownloadService.java:305) + line 51

**Problem:**
- Multiple queries for same statement (N+1 problem)
- Line 305-310: Separate query just for account number
- Line 51: Another query for full statement
- No caching of frequently accessed statements

**Solution:** Single query + caching
```java
@Cacheable(value = "statements", key = "#statementId")
private Statement getStatement(UUID statementId) {
    return statementRepository.findById(statementId)
        .orElseThrow(() -> new StatementNotFoundException("Not found"));
}
```

---

### 4. Missing Input Validation ⚠️ MEDIUM

**Location:** [`validateAndStream()`](src/main/java/com/example/statementservice/service/DownloadService.java:38)

**Problem:**
- No null checks on required parameters
- No token format validation (length, characters)
- Unnecessary database queries for invalid tokens

**Solution:**
```java
private void validateRequest(String token, String performedBy) {
    if (token == null || token.length() < 10 || token.length() > 500) {
        throw new InvalidInputException("Invalid token format");
    }
    if (performedBy == null || performedBy.isBlank()) {
        throw new InvalidInputException("Performed by required");
    }
}
```

---

### 5. Synchronous Audit Logging Blocks Response ⚠️ LOW

**Location:** [`decryptAndStream()`](src/main/java/com/example/statementservice/service/DownloadService.java:274)

**Problem:** Audit logging blocks download response, impacts performance

**Solution:** Async audit logging
```java
private void recordAuditAsync(AuditAction action, Statement statement, 
                              SignedLink link, Map<String, Object> details) {
    CompletableFuture.runAsync(() -> {
        try {
            auditService.record(action.getValue(), statement.getId(), 
                statement.getAccountNumber(), link.getId(), 
                performedBy, details);
        } catch (Exception e) {
            log.error("Async audit failed", e);
        }
    });
}
```

---

### 6. File System Coupling ⚠️ LOW

**Location:** Line 58 - `new File(statement.getFilePath())`

**Problem:** 
- Direct file system access prevents cloud storage migration
- Difficult to test without actual files
- Cannot switch to S3/Azure Blob Storage

**Solution:** Abstract file storage
```java
public interface FileStorageProvider {
    InputStream getDecryptedStream(Statement statement) throws IOException;
    boolean fileExists(Statement statement);
}

@Service
public class LocalFileStorageProvider implements FileStorageProvider {
    @Override
    public InputStream getDecryptedStream(Statement statement) throws IOException {
        File file = new File(statement.getFilePath());
        if (!file.exists()) throw new FileNotFoundException();
        return encryptionService.decryptFileToStream(file);
    }
}
```

---

### 7. Missing Observability ⚠️ LOW

**Problem:** No metrics, monitoring, or performance tracking

**Solution:** Add Micrometer metrics
```java
public DownloadResult download(DownloadRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
        DownloadResult result = performDownload(request);
        meterRegistry.counter("download.success").increment();
        sample.stop(meterRegistry.timer("download.duration", "outcome", "success"));
        return result;
    } catch (Exception e) {
        meterRegistry.counter("download.failure", 
            "reason", e.getClass().getSimpleName()).increment();
        throw e;
    }
}
```

---

## Recommended Refactoring

### Complete Refactored Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadService {

    private final SignedLinkService signedLinkService;
    private final StatementRepository statementRepository;
    private final FileStorageProvider fileStorageProvider;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final Cache<UUID, Statement> statementCache;

    public DownloadResult validateAndDownload(DownloadRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Validate inputs
            validateRequest(request);
            
            // Validate and consume link
            LinkValidationResult linkResult = signedLinkService.validateAndConsume(
                request.getToken());
            if (!linkResult.isValid()) {
                return handleInvalidLink(linkResult, request);
            }
            
            // Fetch statement (cached)
            SignedLink link = linkResult.getLink();
            Statement statement = getStatementCached(link.getStatementId());
            
            // Get decrypted stream
            InputStream stream = fileStorageProvider.getDecryptedStream(statement);
            
            // Record success audit asynchronously
            recordAuditAsync(AuditAction.DOWNLOAD_SUCCESS, statement, link, request, null);
            
            // Track metrics
            meterRegistry.counter("download.success").increment();
            sample.stop(meterRegistry.timer("download.duration", "outcome", "success"));
            
            return DownloadResult.success(stream, statement);
            
        } catch (StatementNotFoundException e) {
            return handleError(DownloadOutcome.STATEMENT_NOT_FOUND, e, request);
        } catch (FileNotFoundException e) {
            return handleError(DownloadOutcome.FILE_MISSING, e, request);
        } catch (IOException e) {
            return handleError(DownloadOutcome.DECRYPTION_FAILED, e, request);
        } catch (Exception e) {
            log.error("Unexpected download error", e);
            return handleError(DownloadOutcome.INTERNAL_ERROR, e, request);
        }
    }

    private void validateRequest(DownloadRequest request) {
        if (request.getToken() == null || request.getToken().length() < 10) {
            throw new InvalidInputException("Invalid token");
        }
        if (request.getPerformedBy() == null || request.getPerformedBy().isBlank()) {
            throw new InvalidInputException("Performed by required");
        }
    }

    private Statement getStatementCached(UUID statementId) {
        return statementCache.get(statementId, id -> 
            statementRepository.findById(id)
                .orElseThrow(() -> new StatementNotFoundException("Not found: " + id))
        );
    }

    private void recordAuditAsync(AuditAction action, Statement statement, 
                                  SignedLink link, DownloadRequest request, 
                                  String failureReason) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> details = buildAuditDetails(request, failureReason);
                auditService.record(
                    action.getValue(),
                    statement.getId(),
                    statement.getAccountNumber(),
                    link.getId(),
                    request.getPerformedBy(),
                    details
                );
            } catch (Exception e) {
                log.error("Async audit failed", e);
            }
        });
    }

    private Map<String, Object> buildAuditDetails(DownloadRequest request, 
                                                  String failureReason) {
        Map<String, Object> details = new HashMap<>();
        details.put("ip", request.getClientIp() != null ? request.getClientIp() : "unknown");
        details.put("userAgent", request.getUserAgent() != null ? request.getUserAgent() : "unknown");
        details.put("token", maskToken(request.getToken()));
        if (failureReason != null) {
            details.put("reason", failureReason);
        }
        return details;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
```

---

## Performance Improvements

### Add Statement Caching

```java
@Configuration
public class CacheConfig {
    @Bean
    public Cache<UUID, Statement> statementCache() {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .recordStats()
            .build();
    }
}
```

### Add Rate Limiting

```java
@Service
public class DownloadService {
    private final RateLimiter rateLimiter = RateLimiter.create(100.0); // 100/sec
    
    public DownloadResult validateAndDownload(DownloadRequest request) {
        if (!rateLimiter.tryAcquire(Duration.ofSeconds(1))) {
            throw new RateLimitExceededException("Too many requests");
        }
        // ... rest of logic
    }
}
```

---

## Security Improvements

### Track Failed Attempts

```java
@Component
public class DownloadSecurityService {
    private final Cache<String, AtomicInteger> failedAttempts;
    
    public void recordFailedAttempt(String clientIp) {
        AtomicInteger attempts = failedAttempts.get(clientIp, k -> new AtomicInteger(0));
        int count = attempts.incrementAndGet();
        if (count > 10) {
            log.warn("Suspicious activity from IP: {}, attempts: {}", clientIp, count);
        }
    }
    
    public boolean isBlocked(String clientIp) {
        AtomicInteger attempts = failedAttempts.getIfPresent(clientIp);
        return attempts != null && attempts.get() > 20;
    }
}
```

---

## Testing Recommendations

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class DownloadServiceTest {
    
    @Mock private SignedLinkService signedLinkService;
    @Mock private StatementRepository statementRepository;
    @Mock private FileStorageProvider fileStorageProvider;
    @InjectMocks private DownloadService downloadService;
    
    @Test
    void shouldSuccessfullyDownloadWithValidToken() throws IOException {
        // Given
        DownloadRequest request = createValidRequest();
        SignedLink link = createValidLink();
        Statement statement = createStatement();
        InputStream mockStream = new ByteArrayInputStream("test".getBytes());
        
        when(signedLinkService.validateAndConsume(anyString()))
            .thenReturn(LinkValidationResult.valid(link));
        when(statementRepository.findById(any()))
            .thenReturn(Optional.of(statement));
        when(fileStorageProvider.getDecryptedStream(any()))
            .thenReturn(mockStream);
        
        // When
        DownloadResult result = downloadService.validateAndDownload(request);
        
        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getStream());
    }
    
    @Test
    void shouldHandleExpiredLink() {
        // Given
        DownloadRequest request = createValidRequest();
        SignedLink link = createExpiredLink();
        
        when(signedLinkService.validateAndConsume(anyString()))
            .thenReturn(LinkValidationResult.expired(link));
        
        // When
        DownloadResult result = downloadService.validateAndDownload(request);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals(DownloadOutcome.LINK_EXPIRED_OR_USED, result.getOutcome());
    }
}
```

---

## Migration Strategy

1. **Phase 1**: Add `DownloadResult` with `AutoCloseable` (non-breaking)
2. **Phase 2**: Implement `FileStorageProvider` abstraction
3. **Phase 3**: Add caching and metrics
4. **Phase 4**: Consolidate duplicate methods
5. **Phase 5**: Add async audit logging
6. **Phase 6**: Deploy and monitor

---

## Conclusion

The DownloadService requires refactoring to address:
- ✅ **Resource leaks** - Implement AutoCloseable pattern
- ✅ **Code duplication** - Consolidate methods
- ✅ **Performance** - Add caching and async audit
- ✅ **Testability** - Abstract file storage
- ✅ **Observability** - Add metrics and monitoring

**Priority: HIGH for resource leak fix, MEDIUM for other improvements**

---

## References

- Current Implementation: [`DownloadService.java`](src/main/java/com/example/statementservice/service/DownloadService.java)
- Related Services: [`SignedLinkService.java`](src/main/java/com/example/statementservice/service/SignedLinkService.java), [`EncryptionService.java`](src/main/java/com/example/statementservice/service/EncryptionService.java)
- Controller: [`StatementsController.java`](src/main/java/com/example/statementservice/controller/StatementsController.java)