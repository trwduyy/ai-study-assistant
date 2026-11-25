package com.aistudy.assistant.classroom.repository;

import com.aistudy.assistant.classroom.dto.ClassMemberResponse;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {

    Optional<ClassMember> findByClassroomIdAndUserId(Long classId, Long userId);

    @EntityGraph(attributePaths = {"classroom", "user"})
    Optional<ClassMember> findWithClassroomAndUserByClassroomIdAndUserId(Long classId, Long userId);

    long countByClassroomIdAndStatus(Long classId, ClassMemberStatus status);

    @Query("""
            SELECT COUNT(DISTINCT cm.classroom.id)
            FROM ClassMember cm
            WHERE cm.user.id = :teacherId
              AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND cm.classRole IN (
                  com.aistudy.assistant.classroom.entity.ClassRole.OWNER_TEACHER,
                  com.aistudy.assistant.classroom.entity.ClassRole.CO_TEACHER
              )
            """)
    long countActiveClassesForTeacher(@Param("teacherId") Long teacherId);

    @Query("""
            SELECT cm
            FROM ClassMember cm
            JOIN FETCH cm.user
            WHERE cm.classroom.id = :classId
              AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND cm.classRole IN :roles
            """)
    List<ClassMember> findActiveWithUserByClassIdAndRoles(
            @Param("classId") Long classId,
            @Param("roles") Collection<ClassRole> roles
    );

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.classroom.dto.ClassMemberResponse(
                        cm.id,
                        cm.classroom.id,
                        u.id,
                        u.fullName,
                        u.email,
                        u.avatarUrl,
                        cm.classRole,
                        cm.status,
                        cm.joinedAt
                    )
                    FROM ClassMember cm
                    JOIN cm.user u
                    WHERE cm.classroom.id = :classId
                      AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
                      AND (:role IS NULL OR cm.classRole = :role)
                    """,
            countQuery = """
                    SELECT COUNT(cm)
                    FROM ClassMember cm
                    WHERE cm.classroom.id = :classId
                      AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
                      AND (:role IS NULL OR cm.classRole = :role)
                    """
    )
    Page<ClassMemberResponse> findProjectedActiveMembers(
            @Param("classId") Long classId,
            @Param("role") ClassRole role,
            Pageable pageable
    );
}
