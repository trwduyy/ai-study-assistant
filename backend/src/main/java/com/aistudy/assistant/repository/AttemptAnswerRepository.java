package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    List<AttemptAnswer> findByAttemptId(Long attemptId);

    @Query("SELECT COUNT(ans) FROM AttemptAnswer ans WHERE ans.attempt.user.id = :userId")
    long countTotalQuestionsAttemptedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(ans) FROM AttemptAnswer ans WHERE ans.attempt.user.id = :userId AND ans.isCorrect = true")
    long countCorrectAnswersByUserId(@Param("userId") Long userId);

    @Query("SELECT ans.question.quiz.course.id AS courseId, ans.question.quiz.course.name AS courseTitle, " +
            "ans.question.questionText AS questionText, ans.question.explanation AS explanation, " +
            "COUNT(ans) AS totalAttempts, " +
            "SUM(CASE WHEN ans.isCorrect = true THEN 1 ELSE 0 END) AS correctAnswers " +
            "FROM AttemptAnswer ans " +
            "WHERE ans.attempt.user.id = :userId " +
            "GROUP BY ans.question.quiz.course.id, ans.question.quiz.course.name, ans.question.id, ans.question.questionText, ans.question.explanation " +
            "HAVING (SUM(CASE WHEN ans.isCorrect = true THEN 1 ELSE 0 END) * 1.0 / COUNT(ans)) < 0.6 " +
            "ORDER BY COUNT(ans) DESC")
    List<WeakQuestionProjection> findWeakQuestionsByUserId(@Param("userId") Long userId);

    interface WeakQuestionProjection {
        Long getCourseId();
        String getCourseTitle();
        String getQuestionText();
        String getExplanation();
        Long getTotalAttempts();
        Long getCorrectAnswers();
    }
}
