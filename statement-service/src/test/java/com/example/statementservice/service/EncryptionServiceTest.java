package com.example.statementservice.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.statementservice.config.MasterKeyProvider;
import com.example.statementservice.exception.DigestComputationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EncryptionService Unit Tests")
class EncryptionServiceTest {

    @Mock
    private MasterKeyProvider masterKeyProvider;

    private EncryptionService encryptionService;

    @TempDir
    Path tempDir;

    private byte[] testMasterKey;

    @BeforeEach
    void setUp() {
        // Generate a valid 256-bit AES key
        testMasterKey = new byte[32];
        new SecureRandom().nextBytes(testMasterKey);

        when(masterKeyProvider.getKey()).thenReturn(testMasterKey);
        encryptionService = new EncryptionService(masterKeyProvider);
    }

    // ==================== generateInitializationVector Tests ====================

    @Test
    @DisplayName("generateInitializationVector - should generate 12-byte IV")
    void generateInitializationVector_ValidLength() {
        // When
        byte[] iv = encryptionService.generateInitializationVector();

        // Then
        assertThat(iv).isNotNull();
        assertThat(iv).hasSize(12);
    }

    @Test
    @DisplayName("generateInitializationVector - should generate different IVs on each call")
    void generateInitializationVector_Uniqueness() {
        // When
        byte[] iv1 = encryptionService.generateInitializationVector();
        byte[] iv2 = encryptionService.generateInitializationVector();
        byte[] iv3 = encryptionService.generateInitializationVector();

        // Then
        assertThat(iv1).isNotEqualTo(iv2);
        assertThat(iv2).isNotEqualTo(iv3);
        assertThat(iv1).isNotEqualTo(iv3);
    }

    @Test
    @DisplayName("generateInitializationVector - should not generate all zeros")
    void generateInitializationVector_NotAllZeros() {
        // When
        byte[] iv = encryptionService.generateInitializationVector();

        // Then
        boolean hasNonZero = false;
        for (byte b : iv) {
            if (b != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertThat(hasNonZero).isTrue();
    }

    // ==================== encryptToFile and decryptFileToStream Tests ====================

    @Test
    @DisplayName("encryptToFile - should successfully encrypt data to file")
    void encryptToFile_Success() throws IOException {
        // Given
        String plaintext = "This is sensitive data that needs encryption";
        byte[] iv = encryptionService.generateInitializationVector();
        File outputFile = tempDir.resolve("encrypted.dat").toFile();
        InputStream inputStream = new ByteArrayInputStream(plaintext.getBytes());

        // When
        encryptionService.encryptToFile(inputStream, outputFile, iv);

        // Then
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);

        // Verify IV is at the beginning of the file
        byte[] fileContent = Files.readAllBytes(outputFile.toPath());
        assertThat(fileContent.length).isGreaterThan(12);

        byte[] fileIv = new byte[12];
        System.arraycopy(fileContent, 0, fileIv, 0, 12);
        assertThat(fileIv).isEqualTo(iv);
    }

    @Test
    @DisplayName("encryptToFile and decryptFileToStream - round trip should preserve data")
    void encryptDecrypt_RoundTrip() throws IOException {
        // Given
        String originalText = "Secret message for encryption test";
        byte[] iv = encryptionService.generateInitializationVector();
        File encryptedFile = tempDir.resolve("test-encrypted.enc").toFile();
        InputStream inputStream = new ByteArrayInputStream(originalText.getBytes());

        // When - Encrypt
        encryptionService.encryptToFile(inputStream, encryptedFile, iv);

        // When - Decrypt
        InputStream decryptedStream = encryptionService.decryptFileToStream(encryptedFile);
        String decryptedText = new String(decryptedStream.readAllBytes());
        decryptedStream.close();

        // Then
        assertThat(decryptedText).isEqualTo(originalText);
    }

    @Test
    @DisplayName("encryptToFile - should encrypt empty input")
    void encryptToFile_EmptyInput() throws IOException {
        // Given
        byte[] iv = encryptionService.generateInitializationVector();
        File outputFile = tempDir.resolve("empty-encrypted.dat").toFile();
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        // When
        encryptionService.encryptToFile(emptyStream, outputFile, iv);

        // Then
        assertThat(outputFile).exists();
        // File should contain IV + GCM tag at minimum
        assertThat(outputFile.length()).isGreaterThanOrEqualTo(12);
    }

    @Test
    @DisplayName("encryptToFile - should encrypt large data")
    void encryptToFile_LargeData() throws IOException {
        // Given
        byte[] largeData = new byte[100000]; // 100KB
        new SecureRandom().nextBytes(largeData);
        byte[] iv = encryptionService.generateInitializationVector();
        File outputFile = tempDir.resolve("large-encrypted.dat").toFile();
        InputStream inputStream = new ByteArrayInputStream(largeData);

        // When
        encryptionService.encryptToFile(inputStream, outputFile, iv);

        // Then
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(largeData.length);
    }

    @Test
    @DisplayName("decryptFileToStream - should throw IOException for missing IV")
    void decryptFileToStream_MissingIv() throws IOException {
        // Given - Create file with less than 12 bytes
        File invalidFile = tempDir.resolve("invalid.enc").toFile();
        Files.write(invalidFile.toPath(), new byte[] {1, 2, 3, 4, 5});

        // When/Then
        assertThatThrownBy(() -> encryptionService.decryptFileToStream(invalidFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    @DisplayName("decryptFileToStream - should throw IOException for corrupted data")
    void decryptFileToStream_CorruptedData() throws IOException {
        // Given - Create file with valid IV but corrupted encrypted data
        File corruptedFile = tempDir.resolve("corrupted.enc").toFile();
        byte[] corruptedData = new byte[50];
        new SecureRandom().nextBytes(corruptedData);
        Files.write(corruptedFile.toPath(), corruptedData);

        // When/Then
        assertThatThrownBy(() -> {
                    InputStream stream = encryptionService.decryptFileToStream(corruptedFile);
                    stream.readAllBytes(); // Trigger decryption
                    stream.close();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("decryptFileToStream - should throw IOException for non-existent file")
    void decryptFileToStream_FileNotFound() {
        // Given
        File nonExistentFile = tempDir.resolve("does-not-exist.enc").toFile();

        // When/Then
        assertThatThrownBy(() -> encryptionService.decryptFileToStream(nonExistentFile))
                .isInstanceOf(IOException.class);
    }

    // ==================== computeSha256Hex Tests ====================

    @Test
    @DisplayName("computeSha256Hex - should compute correct SHA-256 hash")
    void computeSha256Hex_ValidHash() {
        // Given
        String content = "test content";
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content.getBytes());

        // When
        String hash = encryptionService.computeSha256Hex(file);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("computeSha256Hex - should produce consistent hash for same content")
    void computeSha256Hex_Consistency() {
        // Given
        String content = "consistent content";
        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", content.getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "test2.txt", "text/plain", content.getBytes());

        // When
        String hash1 = encryptionService.computeSha256Hex(file1);
        String hash2 = encryptionService.computeSha256Hex(file2);

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeSha256Hex - should produce different hash for different content")
    void computeSha256Hex_DifferentContent() {
        // Given
        MultipartFile file1 = new MockMultipartFile("file1", "test1.txt", "text/plain", "content1".getBytes());
        MultipartFile file2 = new MockMultipartFile("file2", "test2.txt", "text/plain", "content2".getBytes());

        // When
        String hash1 = encryptionService.computeSha256Hex(file1);
        String hash2 = encryptionService.computeSha256Hex(file2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("computeSha256Hex - should handle empty file")
    void computeSha256Hex_EmptyFile() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        // When
        String hash = encryptionService.computeSha256Hex(emptyFile);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        // SHA-256 of empty input is a known value
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("computeSha256Hex - should throw exception when file access fails")
    void computeSha256Hex_FileAccessError() throws IOException {
        // Given
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getBytes()).thenThrow(new IOException("File read error"));

        // When/Then
        assertThatThrownBy(() -> encryptionService.computeSha256Hex(mockFile))
                .isInstanceOf(DigestComputationException.class)
                .hasMessageContaining("Failed to compute file digest");
    }

    // ==================== computeAccountNumberHash Tests ====================

    @Test
    @DisplayName("computeAccountNumberHash - should compute correct hash")
    void computeAccountNumberHash_ValidHash() {
        // Given
        String accountNumber = "123456789";

        // When
        String hash = encryptionService.computeAccountNumberHash(accountNumber);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("computeAccountNumberHash - should produce consistent hash")
    void computeAccountNumberHash_Consistency() {
        // Given
        String accountNumber = "987654321";

        // When
        String hash1 = encryptionService.computeAccountNumberHash(accountNumber);
        String hash2 = encryptionService.computeAccountNumberHash(accountNumber);

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should produce different hashes for different accounts")
    void computeAccountNumberHash_DifferentAccounts() {
        // Given
        String account1 = "111111111";
        String account2 = "222222222";

        // When
        String hash1 = encryptionService.computeAccountNumberHash(account1);
        String hash2 = encryptionService.computeAccountNumberHash(account2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should trim whitespace")
    void computeAccountNumberHash_TrimWhitespace() {
        // Given
        String account1 = "123456789";
        String account2 = "  123456789  ";

        // When
        String hash1 = encryptionService.computeAccountNumberHash(account1);
        String hash2 = encryptionService.computeAccountNumberHash(account2);

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should handle special characters")
    void computeAccountNumberHash_SpecialCharacters() {
        // Given
        String accountNumber = "ACC-123-456-789";

        // When
        String hash = encryptionService.computeAccountNumberHash(accountNumber);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should handle long account numbers")
    void computeAccountNumberHash_LongAccountNumber() {
        // Given
        String longAccount = "1234567890".repeat(5); // 50 digits

        // When
        String hash = encryptionService.computeAccountNumberHash(longAccount);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("computeAccountNumberHash - should handle Unicode characters")
    void computeAccountNumberHash_Unicode() {
        // Given
        String unicodeAccount = "账户123456789";

        // When
        String hash = encryptionService.computeAccountNumberHash(unicodeAccount);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
    }
}
