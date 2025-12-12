package com.example.statementservice.service;

import com.example.statementservice.config.MasterKeyProvider;
import com.example.statementservice.exception.DigestComputationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    public static final String ALGORITHM_AES = "AES";
    public static final String ALGORITHM_SHA_256 = "SHA-256";
    public static final String ALGO = "AES/GCM/NoPadding";
    public static final int GCM_TAG_LENGTH = 128; // bits
    public static final int INITIALIZATION_VECTOR_LENGTH = 12; // bytes
    private final MasterKeyProvider masterKeyProvider;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(MasterKeyProvider masterKeyProvider) {
        this.masterKeyProvider = masterKeyProvider;
    }

    public byte[] generateInitializationVector() {
        byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
        random.nextBytes(initializationVector);
        return initializationVector;
    }

    public void encryptToFile(InputStream in, File outFile, byte[] initializationVector) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(outFile)) {
            SecretKeySpec secretKeySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
            Cipher cipher = Cipher.getInstance(ALGO);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);
            // write initialization vector at beginning
            fileOutputStream.write(initializationVector);
            try (CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher)) {
                byte[] readByteBuffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(readByteBuffer)) != -1) {
                    cipherOutputStream.write(readByteBuffer, 0, bytesRead);
                }
            }
        } catch (Exception e) {
            throw new IOException("Encryption failed", e);
        }
    }

    public InputStream decryptFileToStream(File encFile) throws IOException {
        try {
            FileInputStream encFileInputStream = new FileInputStream(encFile);
            byte[] iv = new byte[INITIALIZATION_VECTOR_LENGTH];
            int read = encFileInputStream.read(iv);
            if (read != INITIALIZATION_VECTOR_LENGTH) {
                encFileInputStream.close();
                throw new IOException("Invalid encrypted file format: initialization vector missing");
            }
            SecretKeySpec keySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
            Cipher cipher = Cipher.getInstance(ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return new CipherInputStream(encFileInputStream, cipher);
        } catch (Exception e) {
            throw new IOException("Decryption failed", e);
        }
    }

    public String computeSha256Hex(org.springframework.web.multipart.MultipartFile file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance(ALGORITHM_SHA_256);
            byte[] fileBytes = file.getBytes();
            byte[] hash = digest.digest(fileBytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new DigestComputationException("Failed to compute file digest", e);
        }
    }

    public String computeAccountNumberHash(String accountNumber) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance(ALGORITHM_SHA_256);
            byte[] hash = digest.digest(accountNumber.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder accountNumberHash = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                accountNumberHash.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return accountNumberHash.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
