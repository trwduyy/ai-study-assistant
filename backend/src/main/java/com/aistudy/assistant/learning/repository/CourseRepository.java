package com.aistudy.assistant.learning.repository;

import com.aistudy.assistant.learning.dto.CourseResponse;
import com.aistudy.assistant.learning.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<Course> findByIdAndDeletedAtIsNull(Long id);

    Optional<Course> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    boolean existsByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    long countByOwnerIdAndDeletedAtIsNull(Long ownerId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.learning.dto.CourseResponse(
                        c.id,
                        c.name,
                        c.description,
                        (SELECT COUNT(ch) FROM Chapter ch WHERE ch.course.id = c.id),
                        (SELECT COUNT(m) FROM Material m WHERE m.course.id = c.id AND m.deletedAt IS NULL),
                        c.version,
                        c.createdAt,
                        c.updatedAt
                    )
                    FROM Course c
                    WHERE c.owner.id = :ownerId
                      AND c.deletedAt IS NULL
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM Course c
                    WHERE c.owner.id = :ownerId
                      AND c.deletedAt IS NULL
                    """
    )
    Page<CourseResponse> findProjectedByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);
}
