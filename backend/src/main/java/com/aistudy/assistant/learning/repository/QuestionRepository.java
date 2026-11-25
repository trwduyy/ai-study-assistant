package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("""
            SELECT q
            FROM Question q
            LEFT JOIN FETCH q.topic
            LEFT JOIN FETCH q.sourceChunk
            WHERE q.quiz.id = :quizId
            ORDER BY q.position ASC, q.id ASC
            """)
    List<Question> findByQuizIdWithDetails(@Param("quizId") Long quizId);

    long countByQuizId(Long quizId);
}
