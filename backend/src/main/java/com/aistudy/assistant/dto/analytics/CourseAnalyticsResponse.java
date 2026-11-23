package com.aistudy.assistant.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAnalyticsResponse {

    private Long courseId;
    private String courseTitle;
    private long materialCount;
    private long quizzesCount;
    private long quizzesAttempted;
    private Double averageScore;
    private Double latestScore;

    @Builder.Default
    private List<ScoreTrendItem> scoreTrend = new ArrayList<>();
}
