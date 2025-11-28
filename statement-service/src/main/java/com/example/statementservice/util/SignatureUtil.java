package com.example.statementservice.util;

import com.example.statementservice.exception.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SignatureUtil {

    private static final String HMAC = "HmacSHA256";
    private final byte[] secret;

    public SignatureUtil(String secretKey) {
        this.secret = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    public String signWithMethod(String path, long expires, String method) {
        try {
            String data = method + "|" + path + "|" + expires;
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new SignatureException("Failed to sign path", e);
        }
    }
}
