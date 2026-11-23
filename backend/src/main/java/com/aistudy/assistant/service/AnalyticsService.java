package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.analytics.AnalyticsOverviewResponse;
import com.aistudy.assistant.dto.analytics.CourseAnalyticsResponse;
import com.aistudy.assistant.dto.analytics.ReviewRecommendationResponse;

import java.util.List;

public interface AnalyticsService {

    AnalyticsOverviewResponse getOverviewAnalytics();

    CourseAnalyticsResponse getCourseAnalytics(Long courseId);

    List<ReviewRecommendationResponse> getReviewRecommendations();
}
