package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.dto.AiChatResponse;
import com.aistudy.assistant.ai.entity.AiChat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiChatRepository extends JpaRepository<AiChat, Long> {

    @EntityGraph(attributePaths = {"user", "course"})
    Optional<AiChat> findWithDetailsById(Long id);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.ai.dto.AiChatResponse(
                        c.id,
                        cr.id,
                        c.title,
                        c.createdAt,
                        c.updatedAt
                    )
                    FROM AiChat c
                    LEFT JOIN c.course cr
                    WHERE c.user.id = :userId
                      AND (:courseId IS NULL OR cr.id = :courseId)
                    ORDER BY c.updatedAt DESC, c.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM AiChat c
                    WHERE c.user.id = :userId
                      AND (:courseId IS NULL OR c.course.id = :courseId)
                    """
    )
    Page<AiChatResponse> findProjectedByUserId(
            @Param("userId") Long userId,
            @Param("courseId") Long courseId,
            Pageable pageable
    );
}
