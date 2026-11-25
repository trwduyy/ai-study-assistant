package com.aistudy.assistant.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateStudyPlanRequest(
        @NotNull(message = "Mục tiêu học tập (goalId) là bắt buộc.")
        Long goalId,
        LocalDate fromDate,
        LocalDate toDate
) {
}
