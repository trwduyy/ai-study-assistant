package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCourseIdAndUserIdOrderByCreatedAtDesc(Long courseId, Long userId);

    List<Quiz> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Quiz> findByIdAndUserId(Long id, Long userId);

    long countByCourseId(Long courseId);

    long countByUserId(Long userId);
}
