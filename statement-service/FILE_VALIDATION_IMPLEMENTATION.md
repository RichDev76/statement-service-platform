# File Validation Implementation Guide

This document provides all code changes needed to implement comprehensive file validation for the Statement Service, including file size limits, filename validation, content-type validation, and virus scanning.

---

## Table of Contents
1. [Dependencies](#dependencies)
2. [Configuration](#configuration)
3. [File Size Validation](#file-size-validation)
4. [Filename Validation](#filename-validation)
5. [Content-Type Validation](#content-type-validation)
6. [Virus Scanning](#virus-scanning)
7. [Integration](#integration)
8. [Docker Setup](#docker-setup)

---

## Dependencies

### Add to pom.xml

```xml
<!-- Apache Tika for content type detection -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

---

## Configuration

### Update application.yml

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 50MB
      max-request-size: 50MB
      file-size-threshold: 2KB

statement:
  validation:
    file:
      max-size-bytes: 52428800  # 50MB
      min-size-bytes: 1024      # 1KB
      allowed-extensions:
        - pdf
      max-filename-length: 255
  virus-scan:
    enabled: ${VIRUS_SCAN_ENABLED:true}
    clamav:
      host: ${CLAMAV_HOST:localhost}
      port: ${CLAMAV_PORT:3310}
    timeout: 30000
```

### Create FileValidationProperties.java

`src/main/java/com/example/statementservice/config/FileValidationProperties.java`

```java
package com.example.statementservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "statement.validation.file")
public class FileValidationProperties {
    private long maxSizeBytes = 52428800L;
    private long minSizeBytes = 1024L;
    private List<String> allowedExtensions = List.of("pdf");
    private int maxFilenameLength = 255;
}
```

---

## File Size Validation

### Create FileSizeExceededException.java

`src/main/java/com/example/statementservice/exception/FileSizeExceededException.java`

```java
package com.example.statementservice.exception;

public class FileSizeExceededException extends RuntimeException {
    private final long actualSize;
    private final long maxSize;
    
    public FileSizeExceededException(long actualSize, long maxSize) {
        super(String.format("File size %d bytes exceeds maximum %d bytes", actualSize, maxSize));
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }
    
    public long getActualSize() { return actualSize; }
    public long getMaxSize() { return maxSize; }
}
```

### Create FileTooSmallException.java

`src/main/java/com/example/statementservice/exception/FileTooSmallException.java`

```java
package com.example.statementservice.exception;

public class FileTooSmallException extends RuntimeException {
    private final long actualSize;
    private final long minSize;
    
    public FileTooSmallException(long actualSize, long minSize) {
        super(String.format("File size %d bytes below minimum %d bytes", actualSize, minSize));
        this.actualSize = actualSize;
        this.minSize = minSize;
    }
    
    public long getActualSize() { return actualSize; }
    public long getMinSize() { return minSize; }
}
```

---

## Filename Validation

### Create InvalidFilenameException.java

`src/main/java/com/example/statementservice/exception/InvalidFilenameException.java`

```java
package com.example.statementservice.exception;

public class InvalidFilenameException extends RuntimeException {
    private final String filename;
    private final String reason;
    
    public InvalidFilenameException(String filename, String reason) {
        super(String.format("Invalid filename '%s': %s", filename, reason));
        this.filename = filename;
        this.reason = reason;
    }
    
    public String getFilename() { return filename; }
    public String getReason() { return reason; }
}
```

### Create FilenameValidator.java

`src/main/java/com/example/statementservice/validator/FilenameValidator.java`

```java
package com.example.statementservice.validator;

import com.example.statementservice.config.FileValidationProperties;
import com.example.statementservice.exception.InvalidFilenameException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class FilenameValidator {
    private final FileValidationProperties properties;
    
    private static final Pattern VALID_FILENAME_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9._\\-\\s]+$");
    private static final Pattern DANGEROUS_PATTERN = 
        Pattern.compile("(\\.\\.|\\/|\\\\|\\||;|&|\\$|`|<|>|\\*|\\?|\\[|\\]|\\{|\\}|\\(|\\)|!|#|%|\\^|~|@)");
    private static final Pattern PATH_TRAVERSAL_PATTERN = 
        Pattern.compile("(\\.\\.\\/|\\.\\.\\\\/|%2e%2e%2f|%2e%2e%5c)");
    
    public void validate(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidFilenameException(filename, "Filename cannot be null or empty");
        }
        
        if (filename.length() > properties.getMaxFilenameLength()) {
            throw new InvalidFilenameException(filename, 
                String.format("Exceeds maximum length of %d", properties.getMaxFilenameLength()));
        }
        
        if (PATH_TRAVERSAL_PATTERN.matcher(filename.toLowerCase()).find()) {
            throw new InvalidFilenameException(filename, "Path traversal attempt detected");
        }
        
        if (DANGEROUS_PATTERN.matcher(filename).find()) {
            throw new InvalidFilenameException(filename, "Contains invalid characters");
        }
        
        if (!VALID_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new InvalidFilenameException(filename, 
                "Contains characters outside allowed set");
        }
        
        if (filename.contains("..")) {
            throw new InvalidFilenameException(filename, "Cannot contain consecutive dots");
        }
        
        if (filename.startsWith(".") || filename.startsWith(" ") || 
            filename.endsWith(".") || filename.endsWith(" ")) {
            throw new InvalidFilenameException(filename, 
                "Cannot start or end with dots or spaces");
        }
        
        String extension = getFileExtension(filename);
        if (!properties.getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new InvalidFilenameException(filename, 
                String.format("Extension '%s' not allowed", extension));
        }
    }
    
    public String sanitizeFilename(String filename) {
        if (filename == null) return null;
        
        String sanitized = filename.replaceAll("[\\\\/]", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._\\-\\s]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        
        if (sanitized.length() > properties.getMaxFilenameLength()) {
            String extension = getFileExtension(sanitized);
            int maxNameLength = properties.getMaxFilenameLength() - extension.length() - 1;
            String nameWithoutExt = sanitized.substring(0, sanitized.lastIndexOf('.'));
            sanitized = nameWithoutExt.substring(0, Math.min(nameWithoutExt.length(), maxNameLength)) 
                + "." + extension;
        }
        
        return sanitized;
    }
    
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
```

---

## Content-Type Validation

### Create ContentTypeValidator.java

`src/main/java/com/example/statementservice/validator/ContentTypeValidator.java`

```java
package com.example.statementservice.validator;

import com.example.statementservice.exception.UnsupportedContentTypeException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class ContentTypeValidator {
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");
    private static final byte[] PDF_SIGNATURE = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
    
    private final Tika tika = new Tika();
    
    public void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedContentTypeException("File is empty or null");
        }
        
        // Check declared content type
        String declaredContentType = file.getContentType();
        if (declaredContentType == null || 
            !ALLOWED_CONTENT_TYPES.contains(declaredContentType.toLowerCase())) {
            throw new UnsupportedContentTypeException(
                String.format("Declared content type '%s' not allowed", declaredContentType));
        }
        
        // Detect actual content type
        String detectedMimeType;
        try (InputStream inputStream = file.getInputStream()) {
            detectedMimeType = tika.detect(inputStream);
        }
        
        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw new UnsupportedContentTypeException(
                String.format("Detected content type '%s' does not match allowed types", 
                    detectedMimeType));
        }
        
        // Verify PDF magic bytes
        if (!verifyPdfSignature(file)) {
            throw new UnsupportedContentTypeException(
                "File does not have valid PDF signature");
        }
    }
    
    private boolean verifyPdfSignature(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[PDF_SIGNATURE.length];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < PDF_SIGNATURE.length) {
                return false;
            }
            
            for (int i = 0; i < PDF_SIGNATURE.length; i++) {
                if (header[i] != PDF_SIGNATURE[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
```

---

## Virus Scanning

### Create VirusScanException.java

`src/main/java/com/example/statementservice/exception/VirusScanException.java`

```java
package com.example.statementservice.exception;

public class VirusScanException extends RuntimeException {
    private final String filename;
    private final String virusName;
    
    public VirusScanException(String filename, String virusName) {
        super(String.format("Virus detected in '%s': %s", filename, virusName));
        this.filename = filename;
        this.virusName = virusName;
    }
    
    public VirusScanException(String message, Throwable cause) {
        super(message, cause);
        this.filename = null;
        this.virusName = null;
    }
    
    public String getFilename() { return filename; }
    public String getVirusName() { return virusName; }
}
```

### Create VirusScanService.java

`src/main/java/com/example/statementservice/service/VirusScanService.java`

```java
package com.example.statementservice.service;

import com.example.statementservice.exception.VirusScanException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class VirusScanService {
    
    @Value("${statement.virus-scan.enabled:true}")
    private boolean virusScanEnabled;
    
    @Value("${statement.virus-scan.clamav.host:localhost}")
    private String clamavHost;
    
    @Value("${statement.virus-scan.clamav.port:3310}")
    private int clamavPort;
    
    @Value("${statement.virus-scan.timeout:30000}")
    private int timeout;
    
    public void scanFile(MultipartFile file) throws IOException {
        if (!virusScanEnabled) {
            log.warn("Virus scanning disabled for '{}'", file.getOriginalFilename());
            return;
        }
        
        log.debug("Scanning '{}' with ClamAV at {}:{}", 
            file.getOriginalFilename(), clamavHost, clamavPort);
        
        try (Socket socket = new Socket(clamavHost, clamavPort)) {
            socket.setSoTimeout(timeout);
            
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            dos.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));
            dos.flush();
            
            try (InputStream inputStream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    dos.writeInt(bytesRead);
                    dos.write(buffer, 0, bytesRead);
                    dos.flush();
                }
                
                dos.writeInt(0);
                dos.flush();
            }
            
            byte[] response = new byte[1024];
            int responseLength = socket.getInputStream().read(response);
            String result = new String(response, 0, responseLength, StandardCharsets.UTF_8).trim();
            
            log.debug("ClamAV result for '{}': {}", file.getOriginalFilename(), result);
            
            if (result.contains("FOUND")) {
                String virusName = extractVirusName(result);
                log.error("Virus detected in '{}': {}", file.getOriginalFilename(), virusName);
                throw new VirusScanException(file.getOriginalFilename(), virusName);
            } else if (!result.contains("OK")) {
                log.error("Unexpected ClamAV response: {}", result);
                throw new VirusScanException("Virus scan failed: " + result, null);
            }
            
            log.info("File '{}' passed virus scan", file.getOriginalFilename());
            
        } catch (IOException e) {
            log.error("Failed to connect to ClamAV at {}:{}", clamavHost, clamavPort, e);
            throw new VirusScanException("Virus scan service unavailable", e);
        }
    }
    
    private String extractVirusName(String result) {
        String[] parts = result.split(":");
        if (parts.length > 1) {
            String virusPart = parts[1].trim();
            return virusPart.replace("FOUND", "").trim();
        }
        return "Unknown";
    }
    
    public boolean isVirusScanEnabled() {
        return virusScanEnabled;
    }
}
```

---

## Integration

### Create FileValidator.java

`src/main/java/com/example/statementservice/validator/FileValidator.java`

```java
package com.example.statementservice.validator;

import com.example.statementservice.config.FileValidationProperties;
import com.example.statementservice.exception.*;
import com.example.statementservice.service.VirusScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator {
    private final FileValidationProperties properties;
    private final FilenameValidator filenameValidator;
    private final ContentTypeValidator contentTypeValidator;
    private final VirusScanService virusScanService;
    
    public void validateUploadedFile(MultipartFile file) throws IOException {
        log.debug("Validating file: {}", file.getOriginalFilename());
        
        validateFileSize(file);
        validateFilename(file);
        validateContentType(file);
        scanForViruses(file);
        
        log.info("File '{}' passed all validations", file.getOriginalFilename());
    }
    
    private void validateFileSize(MultipartFile file) {
        long fileSize = file.getSize();
        
        if (fileSize > properties.getMaxSizeBytes()) {
            throw new FileSizeExceededException(fileSize, properties.getMaxSizeBytes());
        }
        
        if (fileSize < properties.getMinSizeBytes()) {
            throw new FileTooSmallException(fileSize, properties.getMinSizeBytes());
        }
    }
    
    private void validateFilename(MultipartFile file) {
        filenameValidator.validate(file.getOriginalFilename());
    }
    
    private void validateContentType(MultipartFile file) throws IOException {
        contentTypeValidator.validate(file);
    }
    
    private void scanForViruses(MultipartFile file) throws IOException {
        virusScanService.scanFile(file);
    }
}
```

### Update GlobalExceptionHandler.java

Add to existing `src/main/java/com/example/statementservice/exception/advice/GlobalExceptionHandler.java`:

```java
@ExceptionHandler(FileSizeExceededException.class)
public ProblemDetail handleFileSizeExceeded(FileSizeExceededException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(URI.create("https://api.example.com/errors/file-size-exceeded"));
    pd.setTitle("File Size Exceeded");
    pd.setProperty("actualSize", ex.getActualSize());
    pd.setProperty("maxSize", ex.getMaxSize());
    pd.setProperty("errorCode", "FILE_SIZE_EXCEEDED");
    return pd;
}

@ExceptionHandler(FileTooSmallException.class)
public ProblemDetail handleFileTooSmall(FileTooSmallException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(URI.create("https://api.example.com/errors/file-too-small"));
    pd.setTitle("File Too Small");
    pd.setProperty("actualSize", ex.getActualSize());
    pd.setProperty("minSize", ex.getMinSize());
    pd.setProperty("errorCode", "FILE_TOO_SMALL");
    return pd;
}

@ExceptionHandler(InvalidFilenameException.class)
public ProblemDetail handleInvalidFilename(InvalidFilenameException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(URI.create("https://api.example.com/errors/invalid-filename"));
    pd.setTitle("Invalid Filename");
    pd.setProperty("filename", ex.getFilename());
    pd.setProperty("reason", ex.getReason());
    pd.setProperty("errorCode", "INVALID_FILENAME");
    return pd;
}

@ExceptionHandler(VirusScanException.class)
public ProblemDetail handleVirusScan(VirusScanException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(URI.create("https://api.example.com/errors/virus-detected"));
    pd.setTitle("Virus Detected");
    if (ex.getFilename() != null) pd.setProperty("filename", ex.getFilename());
    if (ex.getVirusName() != null) pd.setProperty("virusName", ex.getVirusName());
    pd.setProperty("errorCode", "VIRUS_DETECTED");
    return pd;
}
```

### Update StatementUploadService.java

Add validation call at the beginning of upload method:

```java
@Transactional
public UploadResult uploadStatement(
        MultipartFile file,
        String accountNumber,
        LocalDate statementDate,
        String messageDigest) throws IOException {
    
    // Comprehensive file validation
    fileValidator.validateUploadedFile(file);
    
    // Existing upload logic continues...
}
```

---

## Docker Setup

### Update docker-compose.yml

Add ClamAV service:

```yaml
services:
  # ... existing services ...
  
  clamav:
    image: clamav/clamav:latest
    ports:
      - "3310:3310"
    volumes:
      - clamav-data:/var/lib/clamav
    healthcheck:
      test: ["CMD", "clamdscan", "--ping", "1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s

  app:
    # ... existing config ...
    environment:
      # ... existing env vars ...
      VIRUS_SCAN_ENABLED: "true"
      CLAMAV_HOST: clamav
      CLAMAV_PORT: "3310"
    depends_on:
      - db
      - clamav

volumes:
  db-data:
  clamav-data:
```

---

## Summary

This implementation provides:

1. **File Size Validation**: Min/max size enforcement
2. **Filename Validation**: Character whitelist, length limits, path traversal protection
3. **Content-Type Validation**: MIME type detection, PDF signature verification
4. **Virus Scanning**: ClamAV integration with configurable timeout

### Quick Start

1. Add Apache Tika dependency to `pom.xml`
2. Create all exception classes
3. Create all validator classes
4. Create `FileValidator` integration class
5. Update `GlobalExceptionHandler`
6. Update `StatementUploadService`
7. Update `application.yml`
8. Add ClamAV to `docker-compose.yml`
9. Run: `cd docker && docker-compose up --build`

### Testing

```bash
# Test file size validation
curl -X POST -F "file=@large_file.pdf" -F "accountNumber=123456789" \
  -F "date=2025-11-01" -H "X-Message-Digest: abc..." \
  http://localhost:8080/api/v1/statements/upload

# Test filename validation
curl -X POST -F "file=@../../../etc/passwd.pdf" ...

# Test content type validation
curl -X POST -F "file=@malicious.exe" ...