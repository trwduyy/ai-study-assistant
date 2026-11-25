package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;

import java.time.Instant;

public record PersonalEventResponse(
        Long id,
        Long ownerId,
        EventType type,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        String timezone,
        EventStatus status,
        String meetingUrl,
        EventSource source,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
