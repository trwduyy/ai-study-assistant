package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.dto.AnalyticsOverviewResponse;
import com.aistudy.assistant.learning.dto.FrequentMistakeResponse;
import com.aistudy.assistant.learning.dto.ReviewTopicResponse;
import com.aistudy.assistant.learning.dto.ScoreTrendItemResponse;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse.DashboardOverviewSummary;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse.DashboardReviewTopicSummary;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse.RecentAttemptSummary;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository.TopicPerformanceProjection;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository.OverviewStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final double MAX_SCORE_SCALE = 10.0;

    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview() {
        return computeOverview(SecurityUtils.currentUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoreTrendItemResponse> getScoreTrend(Instant from, Instant to, Long courseId) {
        Long userId = SecurityUtils.currentUserId();
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofDays(30));
        return quizAttemptRepository.findScoreTrend(
                userId,
                AttemptStatus.SUBMITTED,
                courseId,
                effectiveFrom,
                effectiveTo
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FrequentMistakeResponse> getFrequentMistakes(Long courseId, int limit) {
        Long userId = SecurityUtils.currentUserId();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return attemptAnswerRepository.findFrequentMistakes(
                        userId,
                        AttemptStatus.SUBMITTED,
                        courseId,
                        PageRequest.of(0, safeLimit)
                ).stream()
                .map(row -> {
                    long total = row.getTotalAttempts() != null ? row.getTotalAttempts() : 0L;
                    long wrong = row.getWrongCount() != null ? row.getWrongCount() : 0L;
                    double accuracy = total > 0 ? roundTwoDecimals(((total - wrong) * 100.0) / total) : 0.0;
                    return new FrequentMistakeResponse(
                            row.getQuestionId(),
                            row.getCourseId(),
                            row.getCourseName(),
                            row.getTopicId(),
                            row.getTopicTitle(),
                            row.getQuestionContent(),
                            total,
                            wrong,
                            accuracy
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewTopicResponse> getReviewTopics() {
        return computeReviewTopics(SecurityUtils.currentUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDashboardResponse getStudentDashboard() {
        Long userId = SecurityUtils.currentUserId();
        AnalyticsOverviewResponse overview = computeOverview(userId);
        List<RecentAttemptSummary> recentAttempts = quizAttemptRepository.findRecentSubmittedAttempts(
                userId,
                AttemptStatus.SUBMITTED,
                PageRequest.of(0, 5)
        );
        List<DashboardReviewTopicSummary> reviewTopics = computeReviewTopics(userId).stream()
                .limit(5)
                .map(rt -> new DashboardReviewTopicSummary(
                        rt.topicId(),
                        rt.topicTitle(),
                        rt.confidence(),
                        rt.reason()
                ))
                .toList();

        return new StudentDashboardResponse(
                new DashboardOverviewSummary(
                        overview.totalCourses(),
                        overview.totalMaterials(),
                        overview.averageScore(),
                        overview.accuracyRate()
                ),
                recentAttempts,
                reviewTopics,
                List.of(),
                List.of(),
                0L,
                List.of()
        );
    }

    private AnalyticsOverviewResponse computeOverview(Long userId) {
        long totalCourses = courseRepository.countByOwnerIdAndDeletedAtIsNull(userId);
        long totalMaterials = materialRepository.countByOwnerIdAndDeletedAtIsNull(userId);
        OverviewStatsProjection stats = quizAttemptRepository.aggregateOverviewStats(userId, AttemptStatus.SUBMITTED);

        long totalAttempts = stats != null && stats.getTotalAttempts() != null ? stats.getTotalAttempts() : 0L;
        double averageScore = stats != null && stats.getAverageScore() != null
                ? roundTwoDecimals(stats.getAverageScore().doubleValue())
                : 0.0;
        long totalQuestions = stats != null && stats.getTotalQuestionsAttempted() != null
                ? stats.getTotalQuestionsAttempted()
                : 0L;
        long totalCorrect = stats != null && stats.getTotalCorrectAnswers() != null
                ? stats.getTotalCorrectAnswers()
                : 0L;
        double accuracyRate = totalQuestions > 0
                ? roundTwoDecimals((totalCorrect * 100.0) / totalQuestions)
                : 0.0;

        return new AnalyticsOverviewResponse(
                totalCourses,
                totalMaterials,
                totalAttempts,
                averageScore,
                MAX_SCORE_SCALE,
                totalQuestions,
                totalCorrect,
                accuracyRate
        );
    }

    private List<ReviewTopicResponse> computeReviewTopics(Long userId) {
        List<TopicPerformanceProjection> rows = attemptAnswerRepository.findTopicPerformance(
                userId,
                AttemptStatus.SUBMITTED
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> topicIds = rows.stream().map(TopicPerformanceProjection::getTopicId).distinct().toList();
        Map<Long, Material> suggestedByTopic = materialRepository
                .findByTopicIdInAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(topicIds, MaterialStatus.EXTRACTED)
                .stream()
                .filter(m -> m.getTopic() != null)
                .collect(Collectors.toMap(m -> m.getTopic().getId(), m -> m, (first, ignored) -> first));

        return rows.stream()
                .map(row -> {
                    long sampleSize = row.getSampleSize() != null ? row.getSampleSize() : 0L;
                    long correct = row.getCorrectCount() != null ? row.getCorrectCount() : 0L;
                    double accuracy = sampleSize > 0 ? roundTwoDecimals((correct * 100.0) / sampleSize) : 0.0;
                    String confidence = resolveConfidence(sampleSize);
                    String reason = String.format(
                            "Độ chính xác %.1f%% qua %d câu hỏi thuộc chủ đề này.",
                            accuracy,
                            sampleSize
                    );
                    Material suggestedMaterial = suggestedByTopic.get(row.getTopicId());
                    Long suggestedMaterialId = suggestedMaterial != null ? suggestedMaterial.getId() : null;
                    String suggestedAction = suggestedMaterial != null
                            ? "Ôn lại chủ đề " + row.getTopicTitle() + " trong tài liệu " + suggestedMaterial.getFileName() + "."
                            : "Ôn lại lý thuyết và làm thêm bài tập luyện tập cho chủ đề " + row.getTopicTitle() + ".";

                    return new ReviewTopicResponse(
                            row.getCourseId(),
                            row.getCourseName(),
                            row.getTopicId(),
                            row.getTopicTitle(),
                            accuracy,
                            sampleSize,
                            confidence,
                            reason,
                            suggestedMaterialId,
                            suggestedAction
                    );
                })
                .filter(item -> item.accuracyRate() < 85.0 || item.sampleSize() >= 3)
                .toList();
    }

    private String resolveConfidence(long sampleSize) {
        if (sampleSize >= 10) {
            return "HIGH";
        }
        if (sampleSize >= 5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
