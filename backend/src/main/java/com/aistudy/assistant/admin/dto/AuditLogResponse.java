package com.aistudy.assistant.admin.dto;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Long userId,
        String userEmail,
        String action,
        String objectType,
        Long objectId,
        Map<String, Object> metadataJson,
        Instant createdAt
) {
}
