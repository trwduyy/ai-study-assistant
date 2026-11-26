package com.aistudy.assistant.admin.dto;

import com.aistudy.assistant.classroom.entity.ClassroomStatus;

import java.time.Instant;

public record AdminClassroomResponse(
        Long id,
        String code,
        String name,
        String description,
        String subject,
        ClassroomStatus status,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        long memberCount,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
