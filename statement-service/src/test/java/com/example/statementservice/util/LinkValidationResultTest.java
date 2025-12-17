package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.model.ValidationFailureReason;
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
        LinkValidationResult result = LinkValidationResult.notFound();
        assertNotNull(result);
        assertNull(result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.NOT_FOUND, result.getFailureReason());
    }

    @Test
    @DisplayName("Should create used result with link and USED reason")
    void testUsed() {
        LinkValidationResult result = LinkValidationResult.used(mockLink);
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
        LinkValidationResult result = LinkValidationResult.expired(mockLink);
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
        LinkValidationResult result = LinkValidationResult.valid(mockLink);
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
        LinkValidationResult result = LinkValidationResult.used(null);
        assertNotNull(result);
        assertNull(result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.USED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should handle null link in expired method")
    void testExpired_NullLink() {
        LinkValidationResult result = LinkValidationResult.expired(null);
        assertNotNull(result);
        assertNull(result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.EXPIRED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should handle null link in valid method")
    void testValid_NullLink() {
        LinkValidationResult result = LinkValidationResult.valid(null);
        assertNotNull(result);
        assertNull(result.getLink());
        assertTrue(result.isValid());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("Should create result with constructor")
    void testConstructor() {
        LinkValidationResult result = new LinkValidationResult(mockLink, true, null);
        assertNotNull(result);
        assertEquals(mockLink, result.getLink());
        assertTrue(result.isValid());
        assertNull(result.getFailureReason());
    }

    @Test
    @DisplayName("Should create invalid result with constructor")
    void testConstructor_Invalid() {
        LinkValidationResult result = new LinkValidationResult(mockLink, false, ValidationFailureReason.USED);
        assertNotNull(result);
        assertEquals(mockLink, result.getLink());
        assertFalse(result.isValid());
        assertEquals(ValidationFailureReason.USED, result.getFailureReason());
    }

    @Test
    @DisplayName("Should maintain immutability of link reference")
    void testLinkReference() {
        LinkValidationResult result = LinkValidationResult.valid(mockLink);
        String originalToken = mockLink.getToken();
        SignedLink retrievedLink = result.getLink();
        retrievedLink.setToken("modified-token");
        assertEquals("modified-token", mockLink.getToken());
        assertEquals("modified-token", result.getLink().getToken());
        assertNotEquals(originalToken, result.getLink().getToken());
    }

    @Test
    @DisplayName("Should distinguish between different validation failure reasons")
    void testDifferentFailureReasons() {
        LinkValidationResult notFoundResult = LinkValidationResult.notFound();
        LinkValidationResult usedResult = LinkValidationResult.used(mockLink);
        LinkValidationResult expiredResult = LinkValidationResult.expired(mockLink);
        assertNotEquals(notFoundResult.getFailureReason(), usedResult.getFailureReason());
        assertNotEquals(usedResult.getFailureReason(), expiredResult.getFailureReason());
        assertNotEquals(expiredResult.getFailureReason(), notFoundResult.getFailureReason());
    }

    @Test
    @DisplayName("Should indicate all invalid results have isValid as false")
    void testAllInvalidResultsAreFalse() {
        LinkValidationResult notFoundResult = LinkValidationResult.notFound();
        LinkValidationResult usedResult = LinkValidationResult.used(mockLink);
        LinkValidationResult expiredResult = LinkValidationResult.expired(mockLink);
        assertFalse(notFoundResult.isValid());
        assertFalse(usedResult.isValid());
        assertFalse(expiredResult.isValid());
    }

    @Test
    @DisplayName("Should indicate valid result has isValid as true")
    void testValidResultIsTrue() {
        LinkValidationResult result = LinkValidationResult.valid(mockLink);
        assertTrue(result.isValid());
    }
}
