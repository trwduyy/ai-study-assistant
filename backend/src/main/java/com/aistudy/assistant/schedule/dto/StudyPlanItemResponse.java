package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.StudyPlanItemDecision;
import com.aistudy.assistant.schedule.entity.StudyPlanStatus;

import java.time.Instant;
import java.util.List;

public record StudyPlanItemResponse(
        Long id,
        Long planId,
        StudyPlanStatus planStatus,
        String title,
        Instant plannedStart,
        Instant plannedEnd,
        StudyPlanItemDecision decision,
        Long taskId,
        boolean hasConflict,
        List<ConflictingEventSummary> conflictingEvents
) {
}
