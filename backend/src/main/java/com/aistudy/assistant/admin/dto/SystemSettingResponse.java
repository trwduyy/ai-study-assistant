package com.aistudy.assistant.admin.dto;

import java.time.Instant;

public record SystemSettingResponse(
        String settingKey,
        String settingValue,
        String description,
        Long updatedById,
        String updatedByName,
        Instant updatedAt
) {
}
