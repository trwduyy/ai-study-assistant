package com.aistudy.assistant.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewResponse {

    private long totalCourses;
    private long totalMaterials;
    private long totalQuizzesTaken;
    private double averageScore;
    private long totalQuestionsAttempted;
    private long totalCorrectAnswers;
    private double accuracyRate;
}
