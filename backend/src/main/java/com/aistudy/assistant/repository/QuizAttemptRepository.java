package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByQuizIdAndUserIdOrderByStartedAtDesc(Long quizId, Long userId);

    List<QuizAttempt> findByUserIdOrderByStartedAtDesc(Long userId);

    Optional<QuizAttempt> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT AVG(a.score) FROM QuizAttempt a WHERE a.user.id = :userId")
    Double calculateAverageScoreByUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(a.score) FROM QuizAttempt a WHERE a.quiz.course.id = :courseId AND a.user.id = :userId")
    Double calculateAverageScoreByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM QuizAttempt a WHERE a.quiz.course.id = :courseId AND a.user.id = :userId")
    long countAttemptsByCourseIdAndUserId(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("SELECT a FROM QuizAttempt a WHERE a.quiz.course.id = :courseId AND a.user.id = :userId ORDER BY a.startedAt DESC")
    List<QuizAttempt> findAttemptsByCourseIdAndUserIdOrderByStartedAtDesc(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
