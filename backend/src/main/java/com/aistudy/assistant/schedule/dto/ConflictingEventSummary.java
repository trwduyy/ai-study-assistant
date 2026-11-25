package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventType;

import java.time.Instant;

public record ConflictingEventSummary(
        Long eventId,
        String title,
        EventType type,
        Long classId,
        String className,
        Instant startAt,
        Instant endAt,
        EventSource source
) {
}
