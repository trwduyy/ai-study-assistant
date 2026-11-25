package com.aistudy.assistant.schedule.dto;

import java.time.Instant;

/**
 * Serializable preview record stored in {@code calendar_imports.preview_json}.
 */
public record StoredImportPreviewItem(
        int itemIndex,
        String externalId,
        String title,
        String description,
        String location,
        Instant startAt,
        Instant endAt,
        String timezone
) {
}
