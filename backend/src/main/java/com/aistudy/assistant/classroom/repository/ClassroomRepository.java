package com.aistudy.assistant.classroom.repository;

import com.aistudy.assistant.classroom.dto.ClassroomResponse;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    boolean existsByCode(String code);

    Optional<Classroom> findByCode(String code);

    @EntityGraph(attributePaths = "owner")
    Optional<Classroom> findWithOwnerById(Long id);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.classroom.dto.ClassroomResponse(
                        c.id,
                        c.code,
                        c.name,
                        c.description,
                        c.subject,
                        c.avatarUrl,
                        c.startAt,
                        c.endAt,
                        c.status,
                        o.id,
                        o.fullName,
                        cm.classRole,
                        (SELECT COUNT(m2) FROM ClassMember m2
                         WHERE m2.classroom.id = c.id
                           AND m2.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE),
                        c.version,
                        c.createdAt,
                        c.updatedAt
                    )
                    FROM Classroom c
                    JOIN c.owner o
                    JOIN ClassMember cm ON cm.classroom.id = c.id
                    WHERE cm.user.id = :userId
                      AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
                      AND (:status IS NULL OR c.status = :status)
                      AND (:keyword IS NULL
                           OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(COALESCE(c.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM Classroom c
                    JOIN ClassMember cm ON cm.classroom.id = c.id
                    WHERE cm.user.id = :userId
                      AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
                      AND (:status IS NULL OR c.status = :status)
                      AND (:keyword IS NULL
                           OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(COALESCE(c.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """
    )
    Page<ClassroomResponse> findProjectedMyActiveClasses(
            @Param("userId") Long userId,
            @Param("status") ClassroomStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
