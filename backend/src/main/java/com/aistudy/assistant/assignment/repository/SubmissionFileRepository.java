package com.aistudy.assistant.assignment.repository;

import com.aistudy.assistant.assignment.entity.SubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {

    List<SubmissionFile> findBySubmissionIdOrderByIdAsc(Long submissionId);

    Optional<SubmissionFile> findByIdAndSubmissionId(Long id, Long submissionId);

    void deleteBySubmissionId(Long submissionId);

    @Query("SELECT sf.storedPath FROM SubmissionFile sf WHERE sf.submission.assignment.id = :assignmentId")
    List<String> findStoredPathsByAssignmentId(@Param("assignmentId") Long assignmentId);
}
