package com.aistudy.assistant.classroom.repository;

import com.aistudy.assistant.classroom.dto.ClassPostResponse;
import com.aistudy.assistant.classroom.entity.ClassPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassPostRepository extends JpaRepository<ClassPost, Long> {

    @EntityGraph(attributePaths = {"classroom", "author"})
    Optional<ClassPost> findWithAuthorByIdAndClassroomId(Long id, Long classroomId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.classroom.dto.ClassPostResponse(
                        p.id,
                        p.classroom.id,
                        a.id,
                        a.fullName,
                        p.title,
                        p.content,
                        p.pinned,
                        p.createdAt,
                        p.updatedAt
                    )
                    FROM ClassPost p
                    JOIN p.author a
                    WHERE p.classroom.id = :classId
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM ClassPost p
                    WHERE p.classroom.id = :classId
                    """
    )
    Page<ClassPostResponse> findProjectedByClassId(@Param("classId") Long classId, Pageable pageable);
}
