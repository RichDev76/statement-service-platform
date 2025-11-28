package com.example.statementservice.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.statementservice.exception.InvalidAccountNumberException;
import com.example.statementservice.exception.InvalidDateException;
import com.example.statementservice.exception.InvalidMessageDigestException;
import com.example.statementservice.exception.MissingFileException;
import com.example.statementservice.exception.PdfValidationException;
import com.example.statementservice.exception.UnsupportedContentTypeException;
import com.example.statementservice.service.EncryptionService;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationUtil Unit Tests")
class ValidationUtilTest {

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ValidationUtil validationUtil;

    private MultipartFile validPdfFile;
    private String validAccountNumber;
    private String validDate;
    private String validMessageDigest;

    @BeforeEach
    void setUp() {
        // Create a valid PDF file with PDF magic number
        byte[] pdfContent = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        validPdfFile = new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent);

        validAccountNumber = "123456789";
        validDate = "2024-01-15";
        validMessageDigest = "a".repeat(64); // 64 hex characters
    }

    // ==================== validateFileUploadInputs Tests ====================

    @Test
    @DisplayName("validateFileUploadInputs - should pass with all valid inputs")
    void validateFileUploadInputs_Success() {
        // Given
        when(encryptionService.computeSha256Hex(validPdfFile)).thenReturn(validMessageDigest);

        // When/Then
        assertThatCode(() -> validationUtil.validateFileUploadInputs(
                        validPdfFile, validMessageDigest, validAccountNumber, validDate))
                .doesNotThrowAnyException();

        verify(encryptionService).computeSha256Hex(validPdfFile);
    }

    // ==================== validateMessageDigest Tests ====================

    @Test
    @DisplayName("validateMessageDigest - should pass with valid digest")
    void validateMessageDigest_Valid() {
        // Given
        when(encryptionService.computeSha256Hex(validPdfFile)).thenReturn(validMessageDigest);

        // When/Then
        assertThatCode(() -> validationUtil.validateMessageDigest(validPdfFile, validMessageDigest))
                .doesNotThrowAnyException();

        verify(encryptionService).computeSha256Hex(validPdfFile);
    }

    @Test
    @DisplayName("validateMessageDigest - should throw exception for null digest")
    void validateMessageDigest_Null() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validPdfFile, null))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateMessageDigest - should throw exception for empty digest")
    void validateMessageDigest_Empty() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validPdfFile, ""))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateMessageDigest - should throw exception for invalid format (too short)")
    void validateMessageDigest_TooShort() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validPdfFile, "abc123"))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateMessageDigest - should throw exception for invalid format (too long)")
    void validateMessageDigest_TooLong() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validPdfFile, "a".repeat(65)))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateMessageDigest - should throw exception for non-hex characters")
    void validateMessageDigest_NonHex() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validPdfFile, "g".repeat(64)))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest must be a 64-character hex string");

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("validateMessageDigest - should throw exception when digest does not match")
    void validateMessageDigest_Mismatch() {
        // Given
        String differentDigest = "b".repeat(64);
        when(encryptionService.computeSha256Hex(validPdfFile)).thenReturn(differentDigest);

        // When/Then
        assertThatThrownBy(() -> validationUtil.validateMessageDigest(validPdfFile, validMessageDigest))
                .isInstanceOf(InvalidMessageDigestException.class)
                .hasMessageContaining("X-Message-Digest does not match file contents");

        verify(encryptionService).computeSha256Hex(validPdfFile);
    }

    @Test
    @DisplayName("validateMessageDigest - should be case insensitive for hex comparison")
    void validateMessageDigest_CaseInsensitive() {
        // Given
        String upperCaseDigest = "ABCDEF0123456789".repeat(4);
        String lowerCaseDigest = "abcdef0123456789".repeat(4);
        when(encryptionService.computeSha256Hex(validPdfFile)).thenReturn(lowerCaseDigest);

        // When/Then
        assertThatCode(() -> validationUtil.validateMessageDigest(validPdfFile, upperCaseDigest))
                .doesNotThrowAnyException();

        verify(encryptionService).computeSha256Hex(validPdfFile);
    }

    // ==================== validateFileNotEmpty Tests ====================

    @Test
    @DisplayName("validateFileNotEmpty - should pass with valid file")
    void validateFileNotEmpty_Valid() {
        // When/Then
        assertThatCode(() -> validationUtil.validateFileNotEmpty(validPdfFile)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateFileNotEmpty - should throw exception for null file")
    void validateFileNotEmpty_Null() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateFileNotEmpty(null))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    @Test
    @DisplayName("validateFileNotEmpty - should throw exception for empty file")
    void validateFileNotEmpty_Empty() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        // When/Then
        assertThatThrownBy(() -> validationUtil.validateFileNotEmpty(emptyFile))
                .isInstanceOf(MissingFileException.class)
                .hasMessageContaining("file is required");
    }

    // ==================== validateCorrectContentType Tests ====================

    @Test
    @DisplayName("validateCorrectContentType - should pass with PDF content type")
    void validateCorrectContentType_Valid() {
        // When/Then
        assertThatCode(() -> validationUtil.validateCorrectContentType(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateCorrectContentType - should throw exception for wrong content type")
    void validateCorrectContentType_Wrong() {
        // Given
        MultipartFile wrongTypeFile = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        // When/Then
        assertThatThrownBy(() -> validationUtil.validateCorrectContentType(wrongTypeFile))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    @DisplayName("validateCorrectContentType - should throw exception for null content type")
    void validateCorrectContentType_Null() {
        // Given
        MultipartFile nullTypeFile = new MockMultipartFile("file", "test.pdf", null, "content".getBytes());

        // When/Then
        assertThatThrownBy(() -> validationUtil.validateCorrectContentType(nullTypeFile))
                .isInstanceOf(UnsupportedContentTypeException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    // ==================== validateAccountNumber Tests ====================

    @Test
    @DisplayName("validateAccountNumber - should pass with 9-digit account number")
    void validateAccountNumber_NineDigits() {
        // When/Then
        assertThatCode(() -> validationUtil.validateAccountNumber("123456789")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAccountNumber - should pass with 15-digit account number")
    void validateAccountNumber_FifteenDigits() {
        // When/Then
        assertThatCode(() -> validationUtil.validateAccountNumber("123456789012345"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAccountNumber - should pass with 12-digit account number")
    void validateAccountNumber_TwelveDigits() {
        // When/Then
        assertThatCode(() -> validationUtil.validateAccountNumber("123456789012"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAccountNumber - should throw exception for null")
    void validateAccountNumber_Null() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateAccountNumber(null))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    @DisplayName("validateAccountNumber - should throw exception for empty string")
    void validateAccountNumber_Empty() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateAccountNumber(""))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    @DisplayName("validateAccountNumber - should throw exception for too short (8 digits)")
    void validateAccountNumber_TooShort() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateAccountNumber("12345678"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    @DisplayName("validateAccountNumber - should throw exception for too long (16 digits)")
    void validateAccountNumber_TooLong() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateAccountNumber("1234567890123456"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    @DisplayName("validateAccountNumber - should throw exception for non-numeric characters")
    void validateAccountNumber_NonNumeric() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateAccountNumber("12345678A"))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    @Test
    @DisplayName("validateAccountNumber - should throw exception for whitespace")
    void validateAccountNumber_Whitespace() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateAccountNumber("   "))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Invalid account number");
    }

    // ==================== validateDate Tests ====================

    @Test
    @DisplayName("validateDate - should pass with valid date")
    void validateDate_Valid() {
        // When/Then
        assertThatCode(() -> validationUtil.validateDate("2024-01-15")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDate - should pass with valid date on leap year")
    void validateDate_LeapYear() {
        // When/Then
        assertThatCode(() -> validationUtil.validateDate("2024-02-29")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDate - should throw exception for null")
    void validateDate_Null() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate(null))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    @DisplayName("validateDate - should throw exception for empty string")
    void validateDate_Empty() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate(""))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    @DisplayName("validateDate - should throw exception for wrong format")
    void validateDate_WrongFormat() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate("01/15/2024"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    @DisplayName("validateDate - should throw exception for invalid date (Feb 30)")
    void validateDate_InvalidDate() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate("2024-02-30"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    @DisplayName("validateDate - should throw exception for invalid month")
    void validateDate_InvalidMonth() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate("2024-13-01"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    @DisplayName("validateDate - should throw exception for Feb 29 on non-leap year")
    void validateDate_NonLeapYear() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate("2023-02-29"))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    @Test
    @DisplayName("validateDate - should throw exception for whitespace")
    void validateDate_Whitespace() {
        // When/Then
        assertThatThrownBy(() -> validationUtil.validateDate("   "))
                .isInstanceOf(InvalidDateException.class)
                .hasMessageContaining("date must be in YYYY-MM-DD format");
    }

    // ==================== validatePdfMagicNumber Tests ====================

    @Test
    @DisplayName("validatePdfMagicNumber - should pass with valid PDF magic number")
    void validatePdfMagicNumber_Valid() {
        // When/Then
        assertThatCode(() -> validationUtil.validatePdfMagicNumber(validPdfFile))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePdfMagicNumber - should throw exception for non-PDF file")
    void validatePdfMagicNumber_NotPdf() {
        // Given
        MultipartFile nonPdfFile =
                new MockMultipartFile("file", "test.txt", "text/plain", "This is not a PDF".getBytes());

        // When/Then
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(nonPdfFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    @DisplayName("validatePdfMagicNumber - should throw exception for file too small")
    void validatePdfMagicNumber_TooSmall() {
        // Given
        MultipartFile smallFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {0x25, 0x50});

        // When/Then
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(smallFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is too small to be a valid PDF");
    }

    @Test
    @DisplayName("validatePdfMagicNumber - should throw exception for empty file")
    void validatePdfMagicNumber_Empty() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        // When/Then
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(emptyFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is too small to be a valid PDF");
    }

    @Test
    @DisplayName("validatePdfMagicNumber - should throw exception for IOException")
    void validatePdfMagicNumber_IOException() throws IOException {
        // Given
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getInputStream()).thenThrow(new IOException("IO error"));

        // When/Then
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(mockFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("Failed to read file for magic number validation");
    }

    @Test
    @DisplayName("validatePdfMagicNumber - should throw exception for wrong first byte")
    void validatePdfMagicNumber_WrongFirstByte() {
        // Given
        byte[] wrongMagic = new byte[] {0x00, 0x50, 0x44, 0x46};
        MultipartFile wrongFile = new MockMultipartFile("file", "test.pdf", "application/pdf", wrongMagic);

        // When/Then
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(wrongFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }

    @Test
    @DisplayName("validatePdfMagicNumber - should throw exception for wrong last byte")
    void validatePdfMagicNumber_WrongLastByte() {
        // Given
        byte[] wrongMagic = new byte[] {0x25, 0x50, 0x44, 0x00};
        MultipartFile wrongFile = new MockMultipartFile("file", "test.pdf", "application/pdf", wrongMagic);

        // When/Then
        assertThatThrownBy(() -> validationUtil.validatePdfMagicNumber(wrongFile))
                .isInstanceOf(PdfValidationException.class)
                .hasMessageContaining("File is not a valid PDF");
    }
}
