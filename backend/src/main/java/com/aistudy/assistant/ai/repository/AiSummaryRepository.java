package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.dto.AiSummaryResponse;
import com.aistudy.assistant.ai.entity.AiSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiSummaryRepository extends JpaRepository<AiSummary, Long> {

    @EntityGraph(attributePaths = {"material", "user"})
    Optional<AiSummary> findWithDetailsById(Long id);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT new com.aistudy.assistant.ai.dto.AiSummaryResponse(
                s.id,
                s.material.id,
                s.length,
                s.content,
                s.model,
                s.promptVersion,
                s.createdAt,
                s.updatedAt
            )
            FROM AiSummary s
            WHERE s.material.id = :materialId
              AND s.user.id = :userId
              AND s.material.deletedAt IS NULL
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<AiSummaryResponse> findProjectedByMaterialIdAndUserId(
            @Param("materialId") Long materialId,
            @Param("userId") Long userId
    );
}
