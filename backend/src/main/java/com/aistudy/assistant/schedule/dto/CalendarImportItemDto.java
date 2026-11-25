package com.aistudy.assistant.schedule.dto;

import java.time.Instant;
import java.util.List;

public record CalendarImportItemDto(
        int itemIndex,
        String externalId,
        String title,
        String description,
        String location,
        Instant startAt,
        Instant endAt,
        String timezone,
        boolean hasConflict,
        List<ConflictingEventSummary> conflictingEvents
) {
}
