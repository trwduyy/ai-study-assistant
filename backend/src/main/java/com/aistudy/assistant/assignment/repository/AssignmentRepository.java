package com.aistudy.assistant.assignment.repository;

import com.aistudy.assistant.assignment.dto.AssignmentSummaryResponse;
import com.aistudy.assistant.assignment.entity.Assignment;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    interface TeacherUpcomingAssignmentProjection {
        Long getAssignmentId();

        Long getClassId();

        String getClassName();

        String getTitle();

        BigDecimal getMaxScore();

        Instant getDueAt();

        Long getSubmittedCount();

        Long getTotalStudents();
    }

    @EntityGraph(attributePaths = {"classroom", "creator"})
    Optional<Assignment> findWithDetailsById(Long id);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.assignment.dto.AssignmentSummaryResponse(
                        a.id,
                        a.classroom.id,
                        a.title,
                        a.maxScore,
                        a.openAt,
                        a.dueAt,
                        a.allowLate,
                        a.maxSubmissions,
                        s.status,
                        s.isLate,
                        a.version,
                        a.createdAt
                    )
                    FROM Assignment a
                    LEFT JOIN Submission s ON s.assignment.id = a.id AND s.student.id = :studentId
                    WHERE a.classroom.id = :classId
                    """,
            countQuery = """
                    SELECT COUNT(a)
                    FROM Assignment a
                    WHERE a.classroom.id = :classId
                    """
    )
    Page<AssignmentSummaryResponse> findProjectedByClassIdAndStudent(
            @Param("classId") Long classId,
            @Param("studentId") Long studentId,
            Pageable pageable
    );

    @Query("""
            SELECT a.id AS assignmentId,
                   c.id AS classId,
                   c.name AS className,
                   a.title AS title,
                   a.maxScore AS maxScore,
                   a.dueAt AS dueAt,
                   (SELECT COUNT(s) FROM Submission s
                    WHERE s.assignment.id = a.id
                      AND NOT (s.status = com.aistudy.assistant.assignment.entity.SubmissionStatus.RETURNED
                               AND s.gradedAt IS NULL AND s.score IS NULL)) AS submittedCount,
                   (SELECT COUNT(st) FROM ClassMember st
                    WHERE st.classroom.id = c.id
                      AND st.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
                      AND st.classRole = com.aistudy.assistant.classroom.entity.ClassRole.STUDENT) AS totalStudents
            FROM Assignment a
            JOIN a.classroom c
            JOIN ClassMember tm ON tm.classroom.id = c.id
            WHERE tm.user.id = :teacherId
              AND tm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND tm.classRole IN (
                  com.aistudy.assistant.classroom.entity.ClassRole.OWNER_TEACHER,
                  com.aistudy.assistant.classroom.entity.ClassRole.CO_TEACHER
              )
              AND a.dueAt IS NOT NULL
              AND a.dueAt >= :now
            ORDER BY a.dueAt ASC, a.id ASC
            """)
    List<TeacherUpcomingAssignmentProjection> findUpcomingForTeacherDashboard(
            @Param("teacherId") Long teacherId,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            SELECT new com.aistudy.assistant.learning.dto.StudentDashboardResponse$DueAssignmentSummary(
                a.id,
                c.id,
                c.name,
                a.title,
                a.dueAt,
                a.allowLate,
                a.maxScore
            )
            FROM Assignment a
            JOIN a.classroom c
            JOIN ClassMember sm ON sm.classroom.id = c.id
            WHERE sm.user.id = :studentId
              AND sm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND sm.classRole = com.aistudy.assistant.classroom.entity.ClassRole.STUDENT
              AND a.dueAt IS NOT NULL
              AND a.dueAt >= :now
              AND NOT EXISTS (
                  SELECT 1 FROM Submission s
                  WHERE s.assignment.id = a.id
                    AND s.student.id = :studentId
                    AND NOT (s.status = com.aistudy.assistant.assignment.entity.SubmissionStatus.RETURNED
                             AND s.gradedAt IS NULL AND s.score IS NULL)
              )
            ORDER BY a.dueAt ASC, a.id ASC
            """)
    List<StudentDashboardResponse.DueAssignmentSummary> findUpcomingUnsubmittedForStudent(
            @Param("studentId") Long studentId,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            SELECT a
            FROM Assignment a
            JOIN FETCH a.classroom
            WHERE a.dueAt IS NOT NULL
              AND a.dueAt > :now
              AND a.dueAt <= :windowEnd
            """)
    List<Assignment> findDueWithinWindow(
            @Param("now") Instant now,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            SELECT cm.user.id
            FROM ClassMember cm
            WHERE cm.classroom.id = :classId
              AND cm.status = com.aistudy.assistant.classroom.entity.ClassMemberStatus.ACTIVE
              AND cm.classRole = com.aistudy.assistant.classroom.entity.ClassRole.STUDENT
              AND NOT EXISTS (
                  SELECT 1 FROM Submission s
                  WHERE s.assignment.id = :assignmentId
                    AND s.student.id = cm.user.id
                    AND NOT (s.status = com.aistudy.assistant.assignment.entity.SubmissionStatus.RETURNED
                             AND s.gradedAt IS NULL AND s.score IS NULL)
              )
            """)
    List<Long> findActiveStudentIdsWithoutSubmission(
            @Param("assignmentId") Long assignmentId,
            @Param("classId") Long classId
    );
}
