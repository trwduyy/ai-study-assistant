package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.assignment.dto.ClassTestResultsResponse;
import com.aistudy.assistant.learning.dto.QuizAttemptSummaryResponse;
import com.aistudy.assistant.learning.dto.ScoreTrendItemResponse;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.QuizAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    interface OverviewStatsProjection {
        Long getTotalAttempts();

        BigDecimal getAverageScore();

        Long getTotalQuestionsAttempted();

        Long getTotalCorrectAnswers();
    }

    @EntityGraph(attributePaths = {"quiz", "user"})
    Optional<QuizAttempt> findWithQuizAndUserById(Long id);

    @EntityGraph(attributePaths = {"quiz", "user"})
    Optional<QuizAttempt> findWithQuizAndUserByIdAndTestId(Long id, Long testId);

    long countByTestIdAndUserId(Long testId, Long userId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.learning.dto.QuizAttemptSummaryResponse(
                        a.id,
                        a.status,
                        a.score,
                        a.maxScore,
                        a.correctCount,
                        a.totalQuestions,
                        a.durationSeconds,
                        a.startedAt,
                        a.submittedAt
                    )
                    FROM QuizAttempt a
                    WHERE a.quiz.id = :quizId
                      AND a.user.id = :userId
                    ORDER BY a.startedAt DESC, a.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(a)
                    FROM QuizAttempt a
                    WHERE a.quiz.id = :quizId
                      AND a.user.id = :userId
                    """
    )
    Page<QuizAttemptSummaryResponse> findProjectedByQuizIdAndUserId(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(a) AS totalAttempts,
                   COALESCE(AVG(a.score), 0) AS averageScore,
                   COALESCE(SUM(a.totalQuestions), 0) AS totalQuestionsAttempted,
                   COALESCE(SUM(a.correctCount), 0) AS totalCorrectAnswers
            FROM QuizAttempt a
            WHERE a.user.id = :userId
              AND a.status = :status
              AND a.quiz.deletedAt IS NULL
            """)
    OverviewStatsProjection aggregateOverviewStats(
            @Param("userId") Long userId,
            @Param("status") AttemptStatus status
    );

    @Query("""
            SELECT new com.aistudy.assistant.learning.dto.ScoreTrendItemResponse(
                a.id,
                q.id,
                q.title,
                q.course.id,
                a.score,
                a.maxScore,
                a.submittedAt
            )
            FROM QuizAttempt a
            JOIN a.quiz q
            WHERE a.user.id = :userId
              AND a.status = :status
              AND q.deletedAt IS NULL
              AND (:courseId IS NULL OR q.course.id = :courseId)
              AND a.submittedAt >= :from
              AND a.submittedAt <= :to
            ORDER BY a.submittedAt ASC, a.id ASC
            """)
    List<ScoreTrendItemResponse> findScoreTrend(
            @Param("userId") Long userId,
            @Param("status") AttemptStatus status,
            @Param("courseId") Long courseId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT new com.aistudy.assistant.learning.dto.StudentDashboardResponse$RecentAttemptSummary(
                a.id,
                q.id,
                q.title,
                CAST(a.score AS double),
                CAST(a.maxScore AS double),
                a.submittedAt
            )
            FROM QuizAttempt a
            JOIN a.quiz q
            WHERE a.user.id = :userId
              AND a.status = :status
              AND q.deletedAt IS NULL
            ORDER BY a.submittedAt DESC, a.id DESC
            """)
    List<StudentDashboardResponse.RecentAttemptSummary> findRecentSubmittedAttempts(
            @Param("userId") Long userId,
            @Param("status") AttemptStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT new com.aistudy.assistant.assignment.dto.ClassTestResultsResponse$StudentTestResultDto(
                u.id,
                u.fullName,
                u.email,
                COUNT(qa.id),
                CAST(MAX(CASE WHEN qa.status = com.aistudy.assistant.learning.entity.AttemptStatus.SUBMITTED
                             THEN qa.score ELSE NULL END) AS double),
                MAX(CASE WHEN qa.status = com.aistudy.assistant.learning.entity.AttemptStatus.SUBMITTED
                         THEN qa.submittedAt ELSE NULL END)
            )
            FROM ClassMember cm
            JOIN cm.user u
            LEFT JOIN QuizAttempt qa ON qa.testId = :testId AND qa.user.id = u.id
            WHERE cm.classroom.id = :classId
              AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND cm.classRole = com.aistudy.assistant.classroom.entity.ClassRole.STUDENT
            GROUP BY u.id, u.fullName, u.email
            ORDER BY u.fullName ASC, u.id ASC
            """)
    List<ClassTestResultsResponse.StudentTestResultDto> findClassTestStudentResults(
            @Param("classId") Long classId,
            @Param("testId") Long testId
    );
}
