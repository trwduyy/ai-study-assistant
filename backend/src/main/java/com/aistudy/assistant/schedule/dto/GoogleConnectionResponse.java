package com.aistudy.assistant.schedule.dto;

import java.time.Instant;

public record GoogleConnectionResponse(
        String provider,
        boolean connected,
        Instant connectedAt,
        Instant lastSyncedAt
) {
}
