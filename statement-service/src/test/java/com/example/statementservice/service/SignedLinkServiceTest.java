package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.repository.SignedLinkRepository;
import com.example.statementservice.util.LinkValidationResult;
import com.example.statementservice.util.SignatureUtil;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignedLinkService Unit Tests")
class SignedLinkServiceTest {

    @Mock
    private SignedLinkRepository signedLinkRepository;

    @Mock
    private SignatureUtil signatureUtil;

    @InjectMocks
    private SignedLinkService signedLinkService;

    private UUID testStatementId;
    private String testToken;
    private String testCreatedBy;
    private String testBasePath;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testToken = "test-signature-token";
        testCreatedBy = "testUser";
        testBasePath = "/api/v1/statements/download/test.pdf";

        // Set default configuration values
        ReflectionTestUtils.setField(signedLinkService, "defaultExpirySeconds", 900L);
        ReflectionTestUtils.setField(signedLinkService, "downloadPath", "/api/v1/statements/download/");
    }

    // ==================== createSignedLink Tests ====================

    @Test
    @DisplayName("createSignedLink - should create and save single-use link")
    void createSignedLink_SingleUse() {
        // Given
        when(signatureUtil.signWithMethod(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SignedLink result = signedLinkService.createSignedLink(testStatementId, true, testCreatedBy, testBasePath);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(testStatementId);
        assertThat(result.getToken()).isEqualTo(testToken);
        assertThat(result.isSingleUse()).isTrue();
        assertThat(result.isUsed()).isFalse();
        assertThat(result.getCreatedBy()).isEqualTo(testCreatedBy);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(result.getCreatedAt());

        verify(signatureUtil).signWithMethod(eq(testBasePath), anyLong(), eq("GET"));
        verify(signedLinkRepository).save(any(SignedLink.class));
    }

    @Test
    @DisplayName("createSignedLink - should create multi-use link")
    void createSignedLink_MultiUse() {
        // Given
        when(signatureUtil.signWithMethod(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SignedLink result = signedLinkService.createSignedLink(testStatementId, false, testCreatedBy, testBasePath);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSingleUse()).isFalse();
        assertThat(result.isUsed()).isFalse();

        verify(signedLinkRepository).save(any(SignedLink.class));
    }

    @Test
    @DisplayName("createSignedLink - should use default expiry time")
    void createSignedLink_DefaultExpiry() {
        // Given
        when(signatureUtil.signWithMethod(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OffsetDateTime beforeCreation = OffsetDateTime.now();

        // When
        SignedLink result = signedLinkService.createSignedLink(testStatementId, true, testCreatedBy, testBasePath);

        // Then
        OffsetDateTime expectedExpiry = beforeCreation.plusSeconds(900);
        assertThat(result.getExpiresAt()).isAfterOrEqualTo(expectedExpiry.minusSeconds(2));
        assertThat(result.getExpiresAt()).isBeforeOrEqualTo(expectedExpiry.plusSeconds(2));
    }

    @Test
    @DisplayName("createSignedLink - should generate signature with correct parameters")
    void createSignedLink_SignatureParameters() {
        // Given
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> expiresCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);

        when(signatureUtil.signWithMethod(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        signedLinkService.createSignedLink(testStatementId, true, testCreatedBy, testBasePath);

        // Then
        verify(signatureUtil).signWithMethod(pathCaptor.capture(), expiresCaptor.capture(), methodCaptor.capture());

        assertThat(pathCaptor.getValue()).isEqualTo(testBasePath);
        assertThat(expiresCaptor.getValue()).isGreaterThan(0);
        assertThat(methodCaptor.getValue()).isEqualTo("GET");
    }

    // Note: buildSignedDownloadLink(String fileName, UUID statementId) method is not tested here
    // because it depends on ServletUriComponentsBuilder which requires servlet request context.
    // This method is better suited for integration testing.

    // ==================== validateAndConsume Tests ====================

    @Test
    @DisplayName("validateAndConsume - should return valid result for valid single-use link")
    void validateAndConsume_ValidSingleUse() {
        // Given
        SignedLink link = createTestLink(true, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNull();

        // Verify link was marked as used
        verify(signedLinkRepository).save(argThat(savedLink -> savedLink.isUsed()));
    }

    @Test
    @DisplayName("validateAndConsume - should return valid result for valid multi-use link without marking as used")
    void validateAndConsume_ValidMultiUse() {
        // Given
        SignedLink link = createTestLink(false, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getLink()).isEqualTo(link);

        // Verify link was NOT marked as used (multi-use)
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return not found for non-existent token")
    void validateAndConsume_NotFound() {
        // Given
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.empty());

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isNull();
        assertThat(result.getFailureReason()).isNotNull();

        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return used result for already used link")
    void validateAndConsume_AlreadyUsed() {
        // Given
        SignedLink link = createTestLink(true, true, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNotNull();

        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return expired result for expired link")
    void validateAndConsume_Expired() {
        // Given
        SignedLink link = createTestLink(true, false, OffsetDateTime.now().minusMinutes(10));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNotNull();

        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should handle link expiring exactly now")
    void validateAndConsume_ExpiringNow() {
        // Given - Link expired 1 second ago
        SignedLink link = createTestLink(true, false, OffsetDateTime.now().minusSeconds(1));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isFalse();

        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should mark only single-use links as used")
    void validateAndConsume_OnlySingleUseMarked() {
        // Given - Multi-use link that's valid
        SignedLink multiUseLink =
                createTestLink(false, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(multiUseLink));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isTrue();

        // Multi-use links should not be saved/marked as used
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should not mark expired single-use link as used")
    void validateAndConsume_ExpiredNotMarkedUsed() {
        // Given
        SignedLink link = createTestLink(true, false, OffsetDateTime.now().minusHours(1));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));

        // When
        LinkValidationResult result = signedLinkService.validateAndConsume(testToken);

        // Then
        assertThat(result.isValid()).isFalse();

        // Should not save expired links
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should use pessimistic locking via findByTokenForUpdate")
    void validateAndConsume_UsesPessimisticLocking() {
        // Given
        SignedLink link = createTestLink(true, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByTokenForUpdate(testToken)).thenReturn(Optional.of(link));
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        signedLinkService.validateAndConsume(testToken);

        // Then
        verify(signedLinkRepository).findByTokenForUpdate(testToken);
    }

    // ==================== Helper Methods ====================

    private SignedLink createTestLink(boolean singleUse, boolean used, OffsetDateTime expiresAt) {
        SignedLink link = new SignedLink();
        link.setId(UUID.randomUUID());
        link.setStatementId(testStatementId);
        link.setToken(testToken);
        link.setSingleUse(singleUse);
        link.setUsed(used);
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        link.setCreatedBy(testCreatedBy);
        return link;
    }
}
