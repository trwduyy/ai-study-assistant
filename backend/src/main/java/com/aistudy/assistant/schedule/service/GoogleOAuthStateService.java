package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates and verifies short-lived HMAC-SHA256 signed OAuth {@code state} tokens bound to {@code userId}.
 */
@Service
public class GoogleOAuthStateService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long STATE_TTL_SECONDS = 600L;

    private final byte[] hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleOAuthStateService(@Value("${app.security.encryption-key}") String base64EncryptionKey) {
        this.hmacSecret = Base64.getDecoder().decode(base64EncryptionKey.trim());
    }

    public String createState(Long userId) {
        long expiresAt = Instant.now().getEpochSecond() + STATE_TTL_SECONDS;
        byte[] nonceBytes = new byte[12];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String payload = userId + "." + expiresAt + "." + nonce;
        String signature = sign(payload);
        return payload + "." + signature;
    }

    public Long verifyAndExtractUserId(String state) {
        if (!StringUtils.hasText(state)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu tham số state OAuth.");
        }
        String[] parts = state.trim().split("\\.");
        if (parts.length != 4) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tham số state OAuth không hợp lệ.");
        }

        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        String expectedSignature = sign(payload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chữ ký state OAuth không hợp lệ.");
        }

        try {
            Long userId = Long.parseLong(parts[0]);
            long expiresAt = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Phiên ủy quyền Google OAuth đã hết hạn.");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tham số state OAuth không đúng định dạng.");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể ký tham số state OAuth.");
        }
    }
}
