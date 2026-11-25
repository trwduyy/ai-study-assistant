package com.aistudy.assistant.common.security;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenEncryptionServiceTest {

    private final TokenEncryptionService encryptionService = new TokenEncryptionService(
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
    );

    @Test
    @DisplayName("encrypt and decrypt round-trips AES-256-GCM token and uses a random IV each time")
    void encryptDecrypt_RandomIv_RoundTripsExactValue() {
        String rawRefreshToken = "1//0gRandomGoogleRefreshTokenExampleXYZ";

        String encrypted1 = encryptionService.encrypt(rawRefreshToken);
        String encrypted2 = encryptionService.encrypt(rawRefreshToken);

        assertNotEquals(encrypted1, encrypted2, "Random IV must produce distinct ciphertexts");
        assertEquals(rawRefreshToken, encryptionService.decrypt(encrypted1));
        assertEquals(rawRefreshToken, encryptionService.decrypt(encrypted2));
    }

    @Test
    @DisplayName("decrypt rejects tampered ciphertext")
    void decrypt_TamperedCiphertext_ThrowsException() {
        String encrypted = encryptionService.encrypt("1//0gSecretToken");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        AppException ex = assertThrows(AppException.class, () -> encryptionService.decrypt(tampered));
        assertEquals(ErrorCode.GOOGLE_API_ERROR, ex.getErrorCode());
    }
}
