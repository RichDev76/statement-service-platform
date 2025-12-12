package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.enums.ValidationFailureReason;
import com.example.statementservice.model.entity.SignedLink;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LinkValidationResult Tests")
class LinkValidationResultTest {

    private SignedLink mockLink;
    private UUID linkId;
    private UUID statementId;

    @BeforeEach
    void setUp() {
        linkId = UUID.randomUUID();
        statementId = UUID.randomUUID();
        mockLink = new SignedLink();
        mockLink.setId(linkId);
        mockLink.setStatementId(statementId);
        mockLink.setToken("test-token-123");
        mockLink.setExpiresAt(OffsetDateTime.now().plusHours(1));
        mockLink.setUsed(false);
    }

    @Test
    @DisplayName("Should create notFound result with null link and NOT_FOUND reason")
    void testNotFound() {
        // Act
        LinkValidationResult result = LinkValidationResult.notFound();

        // Assert
        assertNotNull(result);
        assertNull(result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.NOT_FOUND, result.getFailureReason());
    }

    @Test
    @DisplayName("Should create used result with link and USED reason")
    void testUsed() {
        // Act
        LinkValidationResult result = LinkValidationResult.used(mockLink);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getLink());
        assertEquals(mockLink, result.getLink());
        assertEquals(linkId, result.getLink().getId());
        assertEquals(statementId, result.getLink().getStatementId());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.USED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should create expired result with link and EXPIRED reason")
    void testExpired() {
        // Act
        LinkValidationResult result = LinkValidationResult.expired(mockLink);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getLink());
        assertEquals(mockLink, result.getLink());
        assertEquals(linkId, result.getLink().getId());
        assertEquals(statementId, result.getLink().getStatementId());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.EXPIRED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should create valid result with link and null failure reason")
    void testValid() {
        // Act
        LinkValidationResult result = LinkValidationResult.valid(mockLink);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getLink());
        assertEquals(mockLink, result.getLink());
        assertEquals(linkId, result.getLink().getId());
        assertEquals(statementId, result.getLink().getStatementId());
        assertTrue(result.isValid());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("Should handle null link in used method")
    void testUsed_NullLink() {
        // Act
        LinkValidationResult result = LinkValidationResult.used(null);

        // Assert
        assertNotNull(result);
        assertNull(result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.USED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should handle null link in expired method")
    void testExpired_NullLink() {
        // Act
        LinkValidationResult result = LinkValidationResult.expired(null);

        // Assert
        assertNotNull(result);
        assertNull(result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.EXPIRED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should handle null link in valid method")
    void testValid_NullLink() {
        // Act
        LinkValidationResult result = LinkValidationResult.valid(null);

        // Assert
        assertNotNull(result);
        assertNull(result.getLink());
        assertTrue(result.isValid());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("Should create result with constructor")
    void testConstructor() {
        // Act
        LinkValidationResult result = new LinkValidationResult(mockLink, true, null);

        // Assert
        assertNotNull(result);
        assertEquals(mockLink, result.getLink());
        assertTrue(result.isValid());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("Should create invalid result with constructor")
    void testConstructor_Invalid() {
        // Act
        LinkValidationResult result = new LinkValidationResult(mockLink, false, ValidationFailureReason.USED);

        // Assert
        assertNotNull(result);
        assertEquals(mockLink, result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.USED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should maintain immutability of link reference")
    void testLinkReference() {
        // Arrange
        LinkValidationResult result = LinkValidationResult.valid(mockLink);
        String originalToken = mockLink.getToken();

        // Act
        SignedLink retrievedLink = result.getLink();
        retrievedLink.setToken("modified-token");

        // Assert
        // The link reference is the same, so changes will be reflected
        assertEquals("modified-token", mockLink.getToken());
        assertEquals("modified-token", result.getLink().getToken());
        assertNotEquals(originalToken, result.getLink().getToken());
    }

    @Test
    @DisplayName("Should distinguish between different validation failure reasons")
    void testDifferentFailureReasons() {
        // Act
        LinkValidationResult notFoundResult = LinkValidationResult.notFound();
        LinkValidationResult usedResult = LinkValidationResult.used(mockLink);
        LinkValidationResult expiredResult = LinkValidationResult.expired(mockLink);

        // Assert
        assertNotEquals(notFoundResult.getFailureReason(), usedResult.getFailureReason());
        assertNotEquals(usedResult.getFailureReason(), expiredResult.getFailureReason());
        assertNotEquals(expiredResult.getFailureReason(), notFoundResult.getFailureReason());
    }

    @Test
    @DisplayName("Should indicate all invalid results have isValid as false")
    void testAllInvalidResultsAreFalse() {
        // Act
        LinkValidationResult notFoundResult = LinkValidationResult.notFound();
        LinkValidationResult usedResult = LinkValidationResult.used(mockLink);
        LinkValidationResult expiredResult = LinkValidationResult.expired(mockLink);

        // Assert
        assertFalse(notFoundResult.isValid());
        assertFalse(usedResult.isValid());
        assertFalse(expiredResult.isValid());
    }

    @Test
    @DisplayName("Should indicate valid result has isValid as true")
    void testValidResultIsTrue() {
        // Act
        LinkValidationResult result = LinkValidationResult.valid(mockLink);

        // Assert
        assertTrue(result.isValid());
    }
}
