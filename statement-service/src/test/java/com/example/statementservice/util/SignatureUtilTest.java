package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.exception.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignatureUtil Tests")
class SignatureUtilTest {

    private SignatureUtil signatureUtil;
    private String secretKey;

    @BeforeEach
    void setUp() {
        secretKey = "test-secret-key-12345";
        signatureUtil = new SignatureUtil(secretKey);
    }

    @Test
    @DisplayName("Should create SignatureUtil with secret key")
    void testConstructor() {
        SignatureUtil util = new SignatureUtil("my-secret");
        assertNotNull(util);
    }

    @Test
    @DisplayName("Should generate signature for valid inputs")
    void testSignWithMethod_ValidInputs() {
        String path = "/api/statements/123";
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertFalse(signature.endsWith("="));
    }

    @Test
    @DisplayName("Should generate consistent signatures for same inputs")
    void testSignWithMethod_Consistency() {
        String path = "/download/file.pdf";
        long expires = 9876543210L;
        String method = "GET";
        String signature1 = signatureUtil.signWithMethod(path, expires, method);
        String signature2 = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different paths")
    void testSignWithMethod_DifferentPaths() {
        long expires = 1234567890L;
        String method = "GET";
        String path1 = "/api/statements/123";
        String path2 = "/api/statements/456";
        String signature1 = signatureUtil.signWithMethod(path1, expires, method);
        String signature2 = signatureUtil.signWithMethod(path2, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different expiration times")
    void testSignWithMethod_DifferentExpires() {
        String path = "/api/statements/123";
        String method = "GET";
        long expires1 = 1234567890L;
        long expires2 = 9876543210L;
        String signature1 = signatureUtil.signWithMethod(path, expires1, method);
        String signature2 = signatureUtil.signWithMethod(path, expires2, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different methods")
    void testSignWithMethod_DifferentMethods() {
        String path = "/api/statements/123";
        long expires = 1234567890L;
        String method1 = "GET";
        String method2 = "POST";
        String signature1 = signatureUtil.signWithMethod(path, expires, method1);
        String signature2 = signatureUtil.signWithMethod(path, expires, method2);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures with different secret keys")
    void testSignWithMethod_DifferentSecrets() {
        SignatureUtil util1 = new SignatureUtil("secret1");
        SignatureUtil util2 = new SignatureUtil("secret2");
        String path = "/api/statements/123";
        long expires = 1234567890L;
        String method = "GET";
        String signature1 = util1.signWithMethod(path, expires, method);
        String signature2 = util2.signWithMethod(path, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should handle empty path")
    void testSignWithMethod_EmptyPath() {
        String path = "";
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty method")
    void testSignWithMethod_EmptyMethod() {
        String path = "/api/statements/123";
        long expires = 1234567890L;
        String method = "";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle special characters in path")
    void testSignWithMethod_SpecialCharactersInPath() {
        String path = "/api/statements/file%20name.pdf?param=value&other=123";
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle Unicode characters in path")
    void testSignWithMethod_UnicodeCharacters() {
        String path = "/api/statements/文件名.pdf";
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle long path strings")
    void testSignWithMethod_LongPath() {
        StringBuilder longPath = new StringBuilder("/api/statements/");
        for (int i = 0; i < 100; i++) {
            longPath.append("segment").append(i).append("/");
        }
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(longPath.toString(), expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle zero expiration time")
    void testSignWithMethod_ZeroExpires() {
        String path = "/api/statements/123";
        long expires = 0L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle negative expiration time")
    void testSignWithMethod_NegativeExpires() {
        String path = "/api/statements/123";
        long expires = -1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle maximum long expiration time")
    void testSignWithMethod_MaxLongExpires() {
        String path = "/api/statements/123";
        long expires = Long.MAX_VALUE;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should generate valid Base64 URL-encoded signature")
    void testSignWithMethod_Base64UrlEncoding() {
        String path = "/api/statements/123";
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.contains("+"));
        assertFalse(signature.contains("/"));
        assertFalse(signature.endsWith("="));
    }

    @Test
    @DisplayName("Should handle various HTTP methods")
    void testSignWithMethod_VariousHttpMethods() {
        String path = "/api/statements/123";
        long expires = 1234567890L;
        String getSignature = signatureUtil.signWithMethod(path, expires, "GET");
        String postSignature = signatureUtil.signWithMethod(path, expires, "POST");
        String putSignature = signatureUtil.signWithMethod(path, expires, "PUT");
        String deleteSignature = signatureUtil.signWithMethod(path, expires, "DELETE");
        String patchSignature = signatureUtil.signWithMethod(path, expires, "PATCH");
        assertNotNull(getSignature);
        assertNotNull(postSignature);
        assertNotNull(putSignature);
        assertNotNull(deleteSignature);
        assertNotNull(patchSignature);
        assertNotEquals(getSignature, postSignature);
        assertNotEquals(getSignature, putSignature);
        assertNotEquals(postSignature, deleteSignature);
    }

    @Test
    @DisplayName("Should throw exception when signing with empty secret key")
    void testConstructor_EmptySecret() {
        SignatureUtil util = new SignatureUtil("");
        assertNotNull(util);
        assertThrows(SignatureException.class, () -> {
            util.signWithMethod("/path", 123L, "GET");
        });
    }

    @Test
    @DisplayName("Should handle pipe character in data components")
    void testSignWithMethod_PipeCharacterHandling() {
        String path = "/api/statements/file|with|pipes.pdf";
        long expires = 1234567890L;
        String method = "GET";
        String signature = signatureUtil.signWithMethod(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should produce signatures of consistent length")
    void testSignWithMethod_ConsistentLength() {
        String path1 = "/short";
        String path2 = "/very/long/path/with/many/segments/and/parameters";
        long expires = 1234567890L;
        String method = "GET";
        String signature1 = signatureUtil.signWithMethod(path1, expires, method);
        String signature2 = signatureUtil.signWithMethod(path2, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertTrue(signature1.length() > 0);
        assertTrue(signature2.length() > 0);
        assertEquals(signature1.length(), signature2.length());
    }
}
