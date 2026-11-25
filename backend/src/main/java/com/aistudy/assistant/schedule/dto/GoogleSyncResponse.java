package com.aistudy.assistant.schedule.dto;

import java.time.Instant;

public record GoogleSyncResponse(
        String provider,
        int syncedCount,
        int createdCount,
        int updatedCount,
        int cancelledCount,
        Instant lastSyncedAt
) {
}
