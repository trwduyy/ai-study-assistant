package com.aistudy.assistant.repository;

import com.aistudy.assistant.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<ChatSession> findByUserIdAndCourseIdOrderByUpdatedAtDesc(Long userId, Long courseId);

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
