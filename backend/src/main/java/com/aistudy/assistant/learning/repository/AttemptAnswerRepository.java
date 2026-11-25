package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.entity.AttemptAnswer;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    interface MistakeStatsProjection {
        Long getQuestionId();

        Long getCourseId();

        String getCourseName();

        Long getTopicId();

        String getTopicTitle();

        String getQuestionContent();

        Long getTotalAttempts();

        Long getWrongCount();
    }

    interface TopicPerformanceProjection {
        Long getCourseId();

        String getCourseName();

        Long getTopicId();

        String getTopicTitle();

        Long getSampleSize();

        Long getCorrectCount();
    }

    List<AttemptAnswer> findByAttemptId(Long attemptId);

    @Query("""
            SELECT q.id AS questionId,
                   c.id AS courseId,
                   c.name AS courseName,
                   t.id AS topicId,
                   t.title AS topicTitle,
                   q.content AS questionContent,
                   COUNT(aa.id) AS totalAttempts,
                   SUM(CASE WHEN aa.isCorrect = false THEN 1L ELSE 0L END) AS wrongCount
            FROM AttemptAnswer aa
            JOIN aa.attempt a
            JOIN aa.question q
            JOIN q.quiz qz
            JOIN qz.course c
            LEFT JOIN q.topic t
            WHERE a.user.id = :userId
              AND a.status = :status
              AND qz.deletedAt IS NULL
              AND c.deletedAt IS NULL
              AND (:courseId IS NULL OR c.id = :courseId)
            GROUP BY q.id, c.id, c.name, t.id, t.title, q.content
            HAVING SUM(CASE WHEN aa.isCorrect = false THEN 1L ELSE 0L END) > 0
            ORDER BY SUM(CASE WHEN aa.isCorrect = false THEN 1L ELSE 0L END) DESC, COUNT(aa.id) DESC
            """)
    List<MistakeStatsProjection> findFrequentMistakes(
            @Param("userId") Long userId,
            @Param("status") AttemptStatus status,
            @Param("courseId") Long courseId,
            Pageable pageable
    );

    @Query("""
            SELECT c.id AS courseId,
                   c.name AS courseName,
                   t.id AS topicId,
                   t.title AS topicTitle,
                   COUNT(aa.id) AS sampleSize,
                   SUM(CASE WHEN aa.isCorrect = true THEN 1L ELSE 0L END) AS correctCount
            FROM AttemptAnswer aa
            JOIN aa.attempt a
            JOIN aa.question q
            JOIN q.quiz qz
            JOIN qz.course c
            JOIN q.topic t
            WHERE a.user.id = :userId
              AND a.status = :status
              AND qz.deletedAt IS NULL
              AND c.deletedAt IS NULL
            GROUP BY c.id, c.name, t.id, t.title
            HAVING COUNT(aa.id) > 0
            ORDER BY (SUM(CASE WHEN aa.isCorrect = true THEN 1.0 ELSE 0.0 END) / COUNT(aa.id)) ASC, COUNT(aa.id) DESC
            """)
    List<TopicPerformanceProjection> findTopicPerformance(
            @Param("userId") Long userId,
            @Param("status") AttemptStatus status
    );
}
