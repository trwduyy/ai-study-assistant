package com.aistudy.assistant.meeting.dto;

import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;

import java.time.Instant;

public record ClassEventResponse(
        Long id,
        Long ownerId,
        Long classId,
        String className,
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
