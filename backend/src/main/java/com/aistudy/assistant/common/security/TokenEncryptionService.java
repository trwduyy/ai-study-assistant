package com.aistudy.assistant.common.security;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts external OAuth refresh tokens using AES-256-GCM with a random 96-bit IV per operation.
 */
@Service
public class TokenEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(@Value("${app.security.encryption-key}") String base64EncryptionKey) {
        if (!StringUtils.hasText(base64EncryptionKey)) {
            throw new IllegalStateException("app.security.encryption-key must be configured.");
        }
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(base64EncryptionKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.security.encryption-key must be valid Base64.", ex);
        }
        if (decodedKey.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("app.security.encryption-key must decode to 32 bytes (256 bits).");
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Giá trị token cần mã hóa không được rỗng.");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer combined = ByteBuffer.allocate(iv.length + cipherBytes.length);
            combined.put(iv);
            combined.put(cipherBytes);
            return Base64.getEncoder().encodeToString(combined.array());
        } catch (GeneralSecurityException ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể mã hóa token bảo mật.");
        }
    }

    public String decrypt(String encryptedText) {
        if (!StringUtils.hasText(encryptedText)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Token tích hợp đã lưu không hợp lệ.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText.trim());
            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted token payload too short");
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Không thể giải mã refresh token Google Calendar.");
        }
    }
}
