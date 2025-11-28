package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.statementservice.enums.DownloadOutcome;
import com.example.statementservice.service.DownloadService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadResponseFactory Tests")
class DownloadResponseFactoryTest {

    @InjectMocks
    private DownloadResponseFactory downloadResponseFactory;

    private String fileName;

    @BeforeEach
    void setUp() {
        fileName = "statement-2023-01.pdf";
    }

    @Test
    @DisplayName("Should build OK response with proper headers and body")
    void testBuild_OkOutcome() {
        // Arrange
        byte[] testData = "PDF content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(testData);
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof InputStreamResource);

        HttpHeaders headers = response.getHeaders();
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, headers.getContentType());
        assertNotNull(headers.getContentDisposition());
        assertTrue(headers.getContentDisposition().toString().contains(fileName));
        assertTrue(headers.getContentDisposition().toString().contains("attachment"));
        assertEquals("no-store, no-cache, must-revalidate", headers.getCacheControl());
        assertEquals("no-cache", headers.getFirst("Pragma"));
    }

    @Test
    @DisplayName("Should build FORBIDDEN response for INVALID_SIGNATURE outcome")
    void testBuild_InvalidSignatureOutcome() {
        // Arrange
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.INVALID_SIGNATURE, Optional.empty());

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should build NOT_FOUND response for LINK_EXPIRED_OR_USED outcome")
    void testBuild_LinkExpiredOrUsedOutcome() {
        // Arrange
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.LINK_EXPIRED_OR_USED, Optional.empty());

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should build NOT_FOUND response for STATEMENT_NOT_FOUND outcome")
    void testBuild_StatementNotFoundOutcome() {
        // Arrange
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.STATEMENT_NOT_FOUND, Optional.empty());

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should build NOT_FOUND response for FILE_MISSING outcome")
    void testBuild_FileMissingOutcome() {
        // Arrange
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.FILE_MISSING, Optional.empty());

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should build NOT_FOUND response for DECRYPTION_FAILED outcome")
    void testBuild_DecryptionFailedOutcome() {
        // Arrange
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.DECRYPTION_FAILED, Optional.empty());

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should handle OK outcome with different file names")
    void testBuild_OkOutcome_DifferentFileNames() {
        // Arrange
        String customFileName = "annual-report-2024.pdf";
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(customFileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().toString().contains(customFileName));
    }

    @Test
    @DisplayName("Should handle OK outcome with file name containing special characters")
    void testBuild_OkOutcome_SpecialCharactersInFileName() {
        // Arrange
        String specialFileName = "statement-2023-01 (copy).pdf";
        InputStream inputStream = new ByteArrayInputStream("data".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(specialFileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders().getContentDisposition());
    }

    @Test
    @DisplayName("Should verify all required security headers are set for OK outcome")
    void testBuild_OkOutcome_SecurityHeaders() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("secure content".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        HttpHeaders headers = response.getHeaders();

        // Verify cache control headers
        assertEquals("no-store, no-cache, must-revalidate", headers.getCacheControl());
        assertEquals("no-cache", headers.getFirst("Pragma"));

        // Verify content disposition is set to attachment (prevents inline display)
        String contentDisposition = headers.getContentDisposition().toString();
        assertTrue(contentDisposition.contains("attachment"));
        assertTrue(contentDisposition.contains(fileName));
    }

    @Test
    @DisplayName("Should handle empty input stream for OK outcome")
    void testBuild_OkOutcome_EmptyInputStream() {
        // Arrange
        InputStream emptyInputStream = new ByteArrayInputStream(new byte[0]);
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(emptyInputStream));

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof InputStreamResource);
    }

    @Test
    @DisplayName("Should set correct content type for OK outcome")
    void testBuild_OkOutcome_ContentType() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());
        DownloadService.DownloadStreamResult result =
                new DownloadService.DownloadStreamResult(DownloadOutcome.OK, Optional.of(inputStream));

        // Act
        ResponseEntity<Resource> response = downloadResponseFactory.build(fileName, result);

        // Assert
        assertNotNull(response);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    }
}
