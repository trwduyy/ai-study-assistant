package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;

import java.time.Instant;

public record CalendarEventResponse(
        Long id,
        Long ownerId,
        Long classId,
        String className,
        Long importId,
        EventType type,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        String timezone,
        EventStatus status,
        String meetingUrl,
        EventSource source,
        String externalId,
        boolean readOnly,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
