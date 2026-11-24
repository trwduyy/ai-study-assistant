package com.aistudy.assistant.controller;

import com.aistudy.assistant.common.ApiResponse;
import com.aistudy.assistant.dto.analytics.AnalyticsOverviewResponse;
import com.aistudy.assistant.dto.analytics.CourseAnalyticsResponse;
import com.aistudy.assistant.dto.analytics.ReviewRecommendationResponse;
import com.aistudy.assistant.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Learning Analytics", description = "Endpoints for student performance dashboards, accuracy metrics, and review suggestions")
@SecurityRequirement(name = "BearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Get high-level learning overview statistics for authenticated user")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview() {
        AnalyticsOverviewResponse response = analyticsService.getOverviewAnalytics();
        return ResponseEntity.ok(ApiResponse.success(response, "Overview analytics retrieved"));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "Get performance and historical score trends for a specific course")
    public ResponseEntity<ApiResponse<CourseAnalyticsResponse>> getCourseAnalytics(
            @PathVariable Long courseId) {
        CourseAnalyticsResponse response = analyticsService.getCourseAnalytics(courseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Course analytics retrieved"));
    }

    @GetMapping("/review-recommendations")
    @Operation(summary = "Get personalized topic and question recommendations based on weak performance")
    public ResponseEntity<ApiResponse<List<ReviewRecommendationResponse>>> getReviewRecommendations() {
        List<ReviewRecommendationResponse> response = analyticsService.getReviewRecommendations();
        return ResponseEntity.ok(ApiResponse.success(response, "Review recommendations retrieved"));
    }
}
