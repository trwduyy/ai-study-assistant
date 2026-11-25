package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.AttemptStatus;

import java.time.Instant;

public record SaveAttemptDraftResponse(
        Long attemptId,
        AttemptStatus status,
        Integer version,
        Instant updatedAt
) {
}
