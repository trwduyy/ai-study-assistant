package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.dto.MaterialResponse;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    @EntityGraph(attributePaths = {"owner", "course", "topic"})
    Optional<Material> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"owner", "course", "topic"})
    List<Material> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

    List<Material> findByOwnerIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(Long ownerId, MaterialStatus status);

    List<Material> findByCourseIdAndOwnerIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
            Long courseId,
            Long ownerId,
            MaterialStatus status
    );

    List<Material> findByTopicIdInAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
            Collection<Long> topicIds,
            MaterialStatus status
    );

    Optional<Material> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    boolean existsByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    List<Material> findByCourseIdAndOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long courseId, Long ownerId);

    long countByOwnerIdAndDeletedAtIsNull(Long ownerId);

    long countByCourseIdAndDeletedAtIsNull(Long courseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Material m SET m.topic = null WHERE m.topic.id = :topicId")
    int clearTopicReferences(@Param("topicId") Long topicId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Material m SET m.topic = null WHERE m.topic.chapter.id = :chapterId")
    int clearTopicReferencesByChapterId(@Param("chapterId") Long chapterId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Material m SET m.deletedAt = :deletedAt WHERE m.course.id = :courseId AND m.deletedAt IS NULL")
    int softDeleteByCourseId(@Param("courseId") Long courseId, @Param("deletedAt") Instant deletedAt);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.learning.dto.MaterialResponse(
                        m.id,
                        m.course.id,
                        t.id,
                        m.fileName,
                        m.mimeType,
                        m.sizeBytes,
                        m.category,
                        m.status,
                        m.pageCount,
                        m.createdAt,
                        m.updatedAt
                    )
                    FROM Material m
                    LEFT JOIN m.topic t
                    WHERE m.course.id = :courseId
                      AND m.owner.id = :ownerId
                      AND m.deletedAt IS NULL
                      AND (:topicId IS NULL OR t.id = :topicId)
                      AND (:category IS NULL OR m.category = :category)
                      AND (:status IS NULL OR m.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(m)
                    FROM Material m
                    WHERE m.course.id = :courseId
                      AND m.owner.id = :ownerId
                      AND m.deletedAt IS NULL
                      AND (:topicId IS NULL OR m.topic.id = :topicId)
                      AND (:category IS NULL OR m.category = :category)
                      AND (:status IS NULL OR m.status = :status)
                    """
    )
    Page<MaterialResponse> findProjectedMaterials(
            @Param("courseId") Long courseId,
            @Param("ownerId") Long ownerId,
            @Param("topicId") Long topicId,
            @Param("category") String category,
            @Param("status") MaterialStatus status,
            Pageable pageable
    );
}
