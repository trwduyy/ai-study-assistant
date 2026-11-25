package com.aistudy.assistant.schedule.integration;

import java.time.Instant;

public record GoogleCalendarEventItem(
        String externalId,
        String status,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        String timezone,
        String meetingUrl
) {
}
