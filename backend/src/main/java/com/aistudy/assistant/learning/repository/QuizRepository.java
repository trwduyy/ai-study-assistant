package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.dto.QuizSummaryResponse;
import com.aistudy.assistant.learning.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @EntityGraph(attributePaths = {"owner", "course"})
    Optional<Quiz> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.learning.dto.QuizSummaryResponse(
                        q.id,
                        q.course.id,
                        q.title,
                        q.difficulty,
                        q.questionCount,
                        q.model,
                        q.promptVersion,
                        q.version,
                        q.createdAt,
                        q.updatedAt
                    )
                    FROM Quiz q
                    WHERE q.owner.id = :ownerId
                      AND q.deletedAt IS NULL
                      AND (:courseId IS NULL OR q.course.id = :courseId)
                    """,
            countQuery = """
                    SELECT COUNT(q)
                    FROM Quiz q
                    WHERE q.owner.id = :ownerId
                      AND q.deletedAt IS NULL
                      AND (:courseId IS NULL OR q.course.id = :courseId)
                    """
    )
    Page<QuizSummaryResponse> findProjectedByOwnerId(
            @Param("ownerId") Long ownerId,
            @Param("courseId") Long courseId,
            Pageable pageable
    );
}
