package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.learning.dto.AnalyticsOverviewResponse;
import com.aistudy.assistant.learning.dto.FrequentMistakeResponse;
import com.aistudy.assistant.learning.dto.ReviewTopicResponse;
import com.aistudy.assistant.learning.dto.ScoreTrendItemResponse;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse;

import java.time.Instant;
import java.util.List;

public interface AnalyticsService {

    AnalyticsOverviewResponse getOverview();

    List<ScoreTrendItemResponse> getScoreTrend(Instant from, Instant to, Long courseId);

    List<FrequentMistakeResponse> getFrequentMistakes(Long courseId, int limit);

    List<ReviewTopicResponse> getReviewTopics();

    StudentDashboardResponse getStudentDashboard();
}
