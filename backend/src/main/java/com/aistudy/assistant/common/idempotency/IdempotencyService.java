package com.aistudy.assistant.common.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Shared idempotency helper ensuring retried mutating requests return identical results or reject payload mismatches.
 */
public interface IdempotencyService {

    Optional<String> findCachedResponse(Long userId, String idempotencyKey, String endpoint, String requestHash);

    <T> Optional<T> findCachedResponse(
            Long userId,
            String idempotencyKey,
            String endpoint,
            String requestHash,
            Class<T> responseType
    );

    void saveResponse(
            Long userId,
            String idempotencyKey,
            String endpoint,
            String requestHash,
            int responseStatus,
            Object responseBody,
            Duration ttl
    );

    String hashPayload(Object payload);
}
