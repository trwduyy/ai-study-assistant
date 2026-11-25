package com.aistudy.assistant.classroom.repository;

import com.aistudy.assistant.classroom.dto.ClassMaterialResponse;
import com.aistudy.assistant.classroom.entity.ClassMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassMaterialRepository extends JpaRepository<ClassMaterial, Long> {

    @EntityGraph(attributePaths = {"classroom", "uploader"})
    Optional<ClassMaterial> findWithClassroomById(Long id);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.classroom.dto.ClassMaterialResponse(
                        m.id,
                        m.classroom.id,
                        u.id,
                        u.fullName,
                        m.fileName,
                        m.mimeType,
                        m.sizeBytes,
                        m.createdAt,
                        m.updatedAt
                    )
                    FROM ClassMaterial m
                    JOIN m.uploader u
                    WHERE m.classroom.id = :classId
                    """,
            countQuery = """
                    SELECT COUNT(m)
                    FROM ClassMaterial m
                    WHERE m.classroom.id = :classId
                    """
    )
    Page<ClassMaterialResponse> findProjectedByClassId(@Param("classId") Long classId, Pageable pageable);
}
