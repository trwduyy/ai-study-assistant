package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.dto.analytics.AnalyticsOverviewResponse;
import com.aistudy.assistant.dto.analytics.CourseAnalyticsResponse;
import com.aistudy.assistant.dto.analytics.ReviewRecommendationResponse;
import com.aistudy.assistant.dto.analytics.ScoreTrendItem;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.entity.QuizAttempt;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.repository.QuizAttemptRepository;
import com.aistudy.assistant.repository.QuizRepository;
import com.aistudy.assistant.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("UTC"));

    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverviewAnalytics() {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        long totalCourses = courseRepository.countByOwnerIdAndDeletedAtIsNull(currentUserId);
        long totalMaterials = materialRepository.countByOwnerIdAndDeletedAtIsNull(currentUserId);
        long totalQuizzesTaken = quizAttemptRepository.countByUserId(currentUserId);

        Double avgScoreVal = quizAttemptRepository.calculateAverageScoreByUserId(currentUserId);
        double averageScore = avgScoreVal != null ? roundToOneDecimal(avgScoreVal) : 0.0;

        long totalQuestionsAttempted = attemptAnswerRepository.countTotalQuestionsAttemptedByUserId(currentUserId);
        long totalCorrectAnswers = attemptAnswerRepository.countCorrectAnswersByUserId(currentUserId);

        double accuracyRate = 0.0;
        if (totalQuestionsAttempted > 0) {
            accuracyRate = roundToTwoDecimals((totalCorrectAnswers * 100.0) / totalQuestionsAttempted);
        }

        return AnalyticsOverviewResponse.builder()
                .totalCourses(totalCourses)
                .totalMaterials(totalMaterials)
                .totalQuizzesTaken(totalQuizzesTaken)
                .averageScore(averageScore)
                .totalQuestionsAttempted(totalQuestionsAttempted)
                .totalCorrectAnswers(totalCorrectAnswers)
                .accuracyRate(accuracyRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseAnalyticsResponse getCourseAnalytics(Long courseId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Course course = courseRepository.findByIdAndOwnerIdAndDeletedAtIsNull(courseId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        long materialCount = materialRepository.countByCourseIdAndDeletedAtIsNull(courseId);
        long quizzesCount = quizRepository.countByCourseId(courseId);
        long quizzesAttempted = quizAttemptRepository.countAttemptsByCourseIdAndUserId(courseId, currentUserId);

        Double avgScoreVal = quizAttemptRepository.calculateAverageScoreByCourseIdAndUserId(courseId, currentUserId);
        Double averageScore = avgScoreVal != null ? roundToOneDecimal(avgScoreVal) : null;

        List<QuizAttempt> attempts = quizAttemptRepository.findAttemptsByCourseIdAndUserIdOrderByStartedAtDesc(courseId, currentUserId);

        Double latestScore = null;
        List<ScoreTrendItem> scoreTrend = new ArrayList<>();

        if (!attempts.isEmpty()) {
            latestScore = attempts.get(0).getScore() != null ? attempts.get(0).getScore().doubleValue() : null;

            // Take up to 10 recent attempts and sort them chronologically for visual trend graph
            List<QuizAttempt> trendAttempts = attempts.stream().limit(10).collect(Collectors.toList());
            Collections.reverse(trendAttempts);

            for (QuizAttempt qa : trendAttempts) {
                String formattedDate = qa.getSubmittedAt() != null
                        ? DATE_FORMATTER.format(qa.getSubmittedAt())
                        : DATE_FORMATTER.format(qa.getStartedAt());

                scoreTrend.add(ScoreTrendItem.builder()
                        .quizId(qa.getQuiz() != null ? qa.getQuiz().getId() : null)
                        .quizTitle(qa.getQuiz() != null ? qa.getQuiz().getTitle() : "Quiz")
                        .score(qa.getScore())
                        .date(formattedDate)
                        .build());
            }
        }

        return CourseAnalyticsResponse.builder()
                .courseId(course.getId())
                .courseTitle(course.getName())
                .materialCount(materialCount)
                .quizzesCount(quizzesCount)
                .quizzesAttempted(quizzesAttempted)
                .averageScore(averageScore)
                .latestScore(latestScore)
                .scoreTrend(scoreTrend)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewRecommendationResponse> getReviewRecommendations() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<AttemptAnswerRepository.WeakQuestionProjection> weakQuestions =
                attemptAnswerRepository.findWeakQuestionsByUserId(currentUserId);

        List<ReviewRecommendationResponse> recommendations = new ArrayList<>();

        for (AttemptAnswerRepository.WeakQuestionProjection item : weakQuestions) {
            double accuracy = item.getTotalAttempts() > 0
                    ? (item.getCorrectAnswers() * 100.0) / item.getTotalAttempts()
                    : 0.0;

            String reason = String.format("Accuracy %.1f%% across %d attempts", accuracy, item.getTotalAttempts());
            String action = String.format("Review related materials in course '%s' before attempting new quizzes.", item.getCourseTitle());

            recommendations.add(ReviewRecommendationResponse.builder()
                    .courseId(item.getCourseId())
                    .courseTitle(item.getCourseTitle())
                    .recommendedTopic(item.getCourseTitle() + " - Key Concepts")
                    .reason(reason)
                    .sampleQuestion(item.getQuestionText())
                    .suggestedAction(action)
                    .build());

            if (recommendations.size() >= 5) {
                break;
            }
        }

        return recommendations;
    }

    private double roundToOneDecimal(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundToTwoDecimals(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
