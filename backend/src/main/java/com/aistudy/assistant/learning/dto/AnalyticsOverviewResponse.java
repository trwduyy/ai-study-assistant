package com.aistudy.assistant.learning.dto;

public record AnalyticsOverviewResponse(
        long totalCourses,
        long totalMaterials,
        long totalAttempts,
        double averageScore,
        double maxScoreScale,
        long totalQuestionsAttempted,
        long totalCorrectAnswers,
        double accuracyRate
) {
}
