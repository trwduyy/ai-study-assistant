package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.StudyPlanItemDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecideStudyPlanItemRequest {

    @NotNull(message = "Quyết định xử lý (decision) là bắt buộc.")
    private StudyPlanItemDecision decision;

    @Size(max = 255, message = "Tiêu đề buổi học tối đa 255 ký tự.")
    private String title;

    private Instant plannedStart;

    private Instant plannedEnd;
}
