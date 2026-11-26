package com.aistudy.assistant.admin.dto;

import com.aistudy.assistant.admin.entity.ReportStatus;

import java.time.Instant;

public record ReportResponse(
        Long id,
        Long reporterId,
        String reporterName,
        String reporterEmail,
        String type,
        String targetType,
        Long targetId,
        String detail,
        ReportStatus status,
        String resolutionNote,
        Long handledById,
        String handledByName,
        Instant handledAt,
        Instant createdAt,
        Instant updatedAt
) {
}
