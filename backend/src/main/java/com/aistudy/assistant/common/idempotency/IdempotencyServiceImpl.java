package com.aistudy.assistant.common.idempotency;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final int MAX_KEY_LENGTH = 64;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findCachedResponse(
            Long userId,
            String idempotencyKey,
            String endpoint,
            String requestHash) {
        String normalizedKey = validateAndNormalizeKey(idempotencyKey);
        Instant now = Instant.now();

        Optional<IdempotencyKey> existingOpt = idempotencyKeyRepository
                .findByUserIdAndIdempotencyKeyAndEndpoint(userId, normalizedKey, endpoint);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKey existing = existingOpt.get();
        if (existing.isExpired(now)) {
            return Optional.empty();
        }

        if (existing.getRequestHash() != null && requestHash != null
                && !existing.getRequestHash().equals(requestHash)) {
            throw new AppException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        return Optional.ofNullable(existing.getResponseBody());
    }

    @Override
    @Transactional(readOnly = true)
    public <T> Optional<T> findCachedResponse(
            Long userId,
            String idempotencyKey,
            String endpoint,
            String requestHash,
            Class<T> responseType) {
        Optional<String> cachedJson = findCachedResponse(userId, idempotencyKey, endpoint, requestHash);
        if (cachedJson.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cachedJson.get(), responseType));
        } catch (JsonProcessingException ex) {
            log.error("Failed to deserialize cached idempotency response for endpoint {}: {}", endpoint, ex.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể khôi phục kết quả đã lưu từ Idempotency-Key.");
        }
    }

    @Override
    @Transactional
    public void saveResponse(
            Long userId,
            String idempotencyKey,
            String endpoint,
            String requestHash,
            int responseStatus,
            Object responseBody,
            Duration ttl) {
        String normalizedKey = validateAndNormalizeKey(idempotencyKey);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl != null ? ttl : Duration.ofHours(24));
        String serializedBody = serializeBody(responseBody);
        String responseHash = sha256Hex(serializedBody);

        IdempotencyKey record = idempotencyKeyRepository
                .findByUserIdAndIdempotencyKeyAndEndpoint(userId, normalizedKey, endpoint)
                .orElseGet(IdempotencyKey::new);

        if (record.getId() != null && !record.isExpired(now)
                && record.getRequestHash() != null && requestHash != null
                && !record.getRequestHash().equals(requestHash)) {
            throw new AppException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        record.setUserId(userId);
        record.setIdempotencyKey(normalizedKey);
        record.setEndpoint(endpoint);
        record.setRequestHash(requestHash);
        record.setResponseStatus(responseStatus);
        record.setResponseHash(responseHash);
        record.setResponseBody(serializedBody);
        record.setExpiresAt(expiresAt);

        idempotencyKeyRepository.save(record);
    }

    @Override
    public String hashPayload(Object payload) {
        String serialized = serializeBody(payload);
        return sha256Hex(serialized);
    }

    private String validateAndNormalizeKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header Idempotency-Key là bắt buộc.");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không được vượt quá 64 ký tự.");
        }
        return trimmed;
    }

    private String serializeBody(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể chuẩn hóa dữ liệu idempotency.");
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
