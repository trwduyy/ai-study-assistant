package com.aistudy.assistant.assignment.repository;

import com.aistudy.assistant.assignment.dto.SubmissionSummaryResponse;
import com.aistudy.assistant.assignment.entity.Submission;
import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @EntityGraph(attributePaths = {"assignment", "assignment.classroom", "student", "gradedBy"})
    Optional<Submission> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"assignment", "assignment.classroom", "student", "gradedBy"})
    Optional<Submission> findWithDetailsByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @Query(
            value = """
                    SELECT new com.aistudy.assistant.assignment.dto.SubmissionSummaryResponse(
                        s.id,
                        s.assignment.id,
                        st.id,
                        st.fullName,
                        st.email,
                        s.status,
                        s.isLate,
                        s.submittedAt,
                        s.score,
                        s.gradedAt,
                        (SELECT COUNT(sf) FROM SubmissionFile sf WHERE sf.submission.id = s.id),
                        s.version
                    )
                    FROM Submission s
                    JOIN s.student st
                    WHERE s.assignment.id = :assignmentId
                      AND (:status IS NULL OR s.status = :status)
                      AND (:late IS NULL OR s.isLate = :late)
                    """,
            countQuery = """
                    SELECT COUNT(s)
                    FROM Submission s
                    WHERE s.assignment.id = :assignmentId
                      AND (:status IS NULL OR s.status = :status)
                      AND (:late IS NULL OR s.isLate = :late)
                    """
    )
    Page<SubmissionSummaryResponse> findProjectedByAssignmentId(
            @Param("assignmentId") Long assignmentId,
            @Param("status") SubmissionStatus status,
            @Param("late") Boolean late,
            Pageable pageable
    );
}
