package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.StudyPlanStatus;

import java.time.Instant;
import java.util.List;

public record StudyPlanResponse(
        Long id,
        String jobId,
        StudyPlanStatus status,
        String rationale,
        String model,
        String promptVersion,
        List<StudyPlanItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
