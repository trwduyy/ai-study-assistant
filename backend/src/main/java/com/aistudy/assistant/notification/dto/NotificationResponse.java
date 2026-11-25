package com.aistudy.assistant.notification.dto;

import java.time.Instant;

/**
 * In-app notification DTO delivered via REST and STOMP {@code /user/queue/notifications}.
 */
public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        String targetType,
        Long targetId,
        Instant readAt,
        Instant createdAt
) {
}
