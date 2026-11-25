package com.aistudy.assistant.learning.dto;

public record FrequentMistakeResponse(
        Long questionId,
        Long courseId,
        String courseName,
        Long topicId,
        String topicTitle,
        String questionContent,
        long totalAttempts,
        long wrongCount,
        double accuracyRate
) {
}
