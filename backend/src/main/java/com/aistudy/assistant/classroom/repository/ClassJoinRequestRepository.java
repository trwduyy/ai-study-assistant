package com.aistudy.assistant.classroom.repository;

import com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse;
import com.aistudy.assistant.classroom.entity.ClassJoinRequest;
import com.aistudy.assistant.classroom.entity.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassJoinRequestRepository extends JpaRepository<ClassJoinRequest, Long> {

    boolean existsByClassroomIdAndStudentIdAndStatus(Long classId, Long studentId, JoinRequestStatus status);

    @EntityGraph(attributePaths = {"classroom", "student", "decidedBy"})
    Optional<ClassJoinRequest> findWithDetailsByIdAndClassroomId(Long id, Long classroomId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse(
                        r.id,
                        c.id,
                        c.name,
                        s.id,
                        s.fullName,
                        s.email,
                        r.status,
                        d.id,
                        d.fullName,
                        r.decidedAt,
                        r.rejectionReason,
                        r.createdAt,
                        r.updatedAt
                    )
                    FROM ClassJoinRequest r
                    JOIN r.classroom c
                    JOIN r.student s
                    LEFT JOIN r.decidedBy d
                    WHERE c.id = :classId
                      AND (:status IS NULL OR r.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM ClassJoinRequest r
                    WHERE r.classroom.id = :classId
                      AND (:status IS NULL OR r.status = :status)
                    """
    )
    Page<ClassJoinRequestResponse> findProjectedByClassIdAndStatus(
            @Param("classId") Long classId,
            @Param("status") JoinRequestStatus status,
            Pageable pageable
    );
}
