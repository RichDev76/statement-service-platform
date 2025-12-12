package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommonUtil Tests")
class CommonUtilTest {

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("Should build URI with HTTP and default port 80")
    void testBuildProblemDetailTypeURI_HttpDefaultPort() {
        // Arrange
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, null);

        // Assert
        assertNotNull(result);
        assertEquals("http://example.com", result.toString());
    }

    @Test
    @DisplayName("Should build URI with HTTPS and default port 443")
    void testBuildProblemDetailTypeURI_HttpsDefaultPort() {
        // Arrange
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(443);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, null);

        // Assert
        assertNotNull(result);
        assertEquals("https://example.com", result.toString());
    }

    @Test
    @DisplayName("Should build URI with HTTP and custom port")
    void testBuildProblemDetailTypeURI_HttpCustomPort() {
        // Arrange
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8080);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, null);

        // Assert
        assertNotNull(result);
        assertEquals("http://example.com:8080", result.toString());
    }

    @Test
    @DisplayName("Should build URI with HTTPS and custom port")
    void testBuildProblemDetailTypeURI_HttpsCustomPort() {
        // Arrange
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(8443);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, null);

        // Assert
        assertNotNull(result);
        assertEquals("https://example.com:8443", result.toString());
    }

    @Test
    @DisplayName("Should build URI with context path")
    void testBuildProblemDetailTypeURI_WithContextPath() {
        // Arrange
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);
        String contextPath = "/api/v1";

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, contextPath);

        // Assert
        assertNotNull(result);
        assertEquals("http://example.com/api/v1", result.toString());
    }

    @Test
    @DisplayName("Should build URI with context path and custom port")
    void testBuildProblemDetailTypeURI_WithContextPathAndCustomPort() {
        // Arrange
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.example.com");
        when(request.getServerPort()).thenReturn(9443);
        String contextPath = "/statement-service";

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, contextPath);

        // Assert
        assertNotNull(result);
        assertEquals("https://api.example.com:9443/statement-service", result.toString());
    }

    @Test
    @DisplayName("Should build URI with empty context path")
    void testBuildProblemDetailTypeURI_EmptyContextPath() {
        // Arrange
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        String contextPath = "";

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, contextPath);

        // Assert
        assertNotNull(result);
        assertEquals("http://localhost:8080", result.toString());
    }

    @Test
    @DisplayName("Should build URI with localhost")
    void testBuildProblemDetailTypeURI_Localhost() {
        // Arrange
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(80);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, null);

        // Assert
        assertNotNull(result);
        assertEquals("http://localhost", result.toString());
    }

    @Test
    @DisplayName("Should build URI with IP address")
    void testBuildProblemDetailTypeURI_IpAddress() {
        // Arrange
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("192.168.1.100");
        when(request.getServerPort()).thenReturn(8080);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, null);

        // Assert
        assertNotNull(result);
        assertEquals("http://192.168.1.100:8080", result.toString());
    }

    @Test
    @DisplayName("Should build URI with subdomain")
    void testBuildProblemDetailTypeURI_Subdomain() {
        // Arrange
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.dev.example.com");
        when(request.getServerPort()).thenReturn(443);

        // Act
        URI result = CommonUtil.buildProblemDetailTypeURI(request, "/errors");

        // Assert
        assertNotNull(result);
        assertEquals("https://api.dev.example.com/errors", result.toString());
    }
}
