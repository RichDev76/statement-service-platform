package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestInfo Tests")
class RequestInfoTest {

    @Test
    @DisplayName("Should create RequestInfo with no-arg constructor")
    void testNoArgConstructor() {
        // Act
        RequestInfo requestInfo = new RequestInfo();

        // Assert
        assertNotNull(requestInfo);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should create RequestInfo with all-args constructor")
    void testAllArgsConstructor() {
        // Arrange
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0";
        String performedBy = "john.doe";

        // Act
        RequestInfo requestInfo = new RequestInfo(clientIp, userAgent, performedBy);

        // Assert
        assertNotNull(requestInfo);
        assertEquals(clientIp, requestInfo.getClientIp());
        assertEquals(userAgent, requestInfo.getUserAgent());
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should set and get clientIp")
    void testSetAndGetClientIp() {
        // Arrange
        RequestInfo requestInfo = new RequestInfo();
        String clientIp = "10.0.0.5";

        // Act
        requestInfo.setClientIp(clientIp);

        // Assert
        assertEquals(clientIp, requestInfo.getClientIp());
    }

    @Test
    @DisplayName("Should set and get userAgent")
    void testSetAndGetUserAgent() {
        // Arrange
        RequestInfo requestInfo = new RequestInfo();
        String userAgent = "Chrome/90.0.4430.93";

        // Act
        requestInfo.setUserAgent(userAgent);

        // Assert
        assertEquals(userAgent, requestInfo.getUserAgent());
    }

    @Test
    @DisplayName("Should set and get performedBy")
    void testSetAndGetPerformedBy() {
        // Arrange
        RequestInfo requestInfo = new RequestInfo();
        String performedBy = "admin";

        // Act
        requestInfo.setPerformedBy(performedBy);

        // Assert
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle null values in all-args constructor")
    void testAllArgsConstructor_NullValues() {
        // Act
        RequestInfo requestInfo = new RequestInfo(null, null, null);

        // Assert
        assertNotNull(requestInfo);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should allow setting null values via setters")
    void testSetters_NullValues() {
        // Arrange
        RequestInfo requestInfo = new RequestInfo("192.168.1.1", "Firefox", "user");

        // Act
        requestInfo.setClientIp(null);
        requestInfo.setUserAgent(null);
        requestInfo.setPerformedBy(null);

        // Assert
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should allow updating values via setters")
    void testUpdateValues() {
        // Arrange
        RequestInfo requestInfo = new RequestInfo("192.168.1.1", "Firefox", "user");

        // Act
        requestInfo.setClientIp("10.0.0.1");
        requestInfo.setUserAgent("Chrome");
        requestInfo.setPerformedBy("admin");

        // Assert
        assertEquals("10.0.0.1", requestInfo.getClientIp());
        assertEquals("Chrome", requestInfo.getUserAgent());
        assertEquals("admin", requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle empty strings")
    void testEmptyStrings() {
        // Act
        RequestInfo requestInfo = new RequestInfo("", "", "");

        // Assert
        assertNotNull(requestInfo);
        assertEquals("", requestInfo.getClientIp());
        assertEquals("", requestInfo.getUserAgent());
        assertEquals("", requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle special characters in fields")
    void testSpecialCharacters() {
        // Arrange
        String clientIp = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"; // IPv6
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        String performedBy = "user@example.com";

        // Act
        RequestInfo requestInfo = new RequestInfo(clientIp, userAgent, performedBy);

        // Assert
        assertEquals(clientIp, requestInfo.getClientIp());
        assertEquals(userAgent, requestInfo.getUserAgent());
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle long strings")
    void testLongStrings() {
        // Arrange
        String longUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59";

        // Act
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setUserAgent(longUserAgent);

        // Assert
        assertEquals(longUserAgent, requestInfo.getUserAgent());
    }

    @Test
    @DisplayName("Should maintain independence between instances")
    void testInstanceIndependence() {
        // Arrange
        RequestInfo info1 = new RequestInfo("192.168.1.1", "Firefox", "user1");
        RequestInfo info2 = new RequestInfo("192.168.1.2", "Chrome", "user2");

        // Act
        info1.setClientIp("10.0.0.1");

        // Assert
        assertEquals("10.0.0.1", info1.getClientIp());
        assertEquals("192.168.1.2", info2.getClientIp());
        assertNotEquals(info1.getClientIp(), info2.getClientIp());
    }

    @Test
    @DisplayName("Should create multiple instances with same values")
    void testMultipleInstancesSameValues() {
        // Arrange
        String clientIp = "192.168.1.100";
        String userAgent = "Safari";
        String performedBy = "testuser";

        // Act
        RequestInfo info1 = new RequestInfo(clientIp, userAgent, performedBy);
        RequestInfo info2 = new RequestInfo(clientIp, userAgent, performedBy);

        // Assert
        assertNotSame(info1, info2);
        assertEquals(info1.getClientIp(), info2.getClientIp());
        assertEquals(info1.getUserAgent(), info2.getUserAgent());
        assertEquals(info1.getPerformedBy(), info2.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle system default values")
    void testSystemDefaultValues() {
        // Act
        RequestInfo requestInfo = new RequestInfo("unknown", "unknown", "system");

        // Assert
        assertEquals("unknown", requestInfo.getClientIp());
        assertEquals("unknown", requestInfo.getUserAgent());
        assertEquals("system", requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle localhost scenarios")
    void testLocalhostScenarios() {
        // Act
        RequestInfo requestInfo = new RequestInfo("127.0.0.1", "PostmanRuntime/7.28.0", "developer");

        // Assert
        assertEquals("127.0.0.1", requestInfo.getClientIp());
        assertEquals("PostmanRuntime/7.28.0", requestInfo.getUserAgent());
        assertEquals("developer", requestInfo.getPerformedBy());
    }
}
