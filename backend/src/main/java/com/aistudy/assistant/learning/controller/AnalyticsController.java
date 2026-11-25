package com.aistudy.assistant.learning.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.learning.dto.AnalyticsOverviewResponse;
import com.aistudy.assistant.learning.dto.FrequentMistakeResponse;
import com.aistudy.assistant.learning.dto.ReviewTopicResponse;
import com.aistudy.assistant.learning.dto.ScoreTrendItemResponse;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse;
import com.aistudy.assistant.learning.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST endpoints for student learning analytics, mistake tracking, review topic guidance, and the student dashboard.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/analytics/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getOverview()));
    }

    @GetMapping("/analytics/score-trend")
    public ResponseEntity<ApiResponse<List<ScoreTrendItemResponse>>> getScoreTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getScoreTrend(from, to, courseId)));
    }

    @GetMapping("/analytics/frequent-mistakes")
    public ResponseEntity<ApiResponse<List<FrequentMistakeResponse>>> getFrequentMistakes(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getFrequentMistakes(courseId, limit)));
    }

    @GetMapping("/analytics/review-topics")
    public ResponseEntity<ApiResponse<List<ReviewTopicResponse>>> getReviewTopics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getReviewTopics()));
    }

    @GetMapping("/dashboard/student")
    public ResponseEntity<ApiResponse<StudentDashboardResponse>> getStudentDashboard() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getStudentDashboard()));
    }
}
