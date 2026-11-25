package com.aistudy.assistant.assignment.repository;

import com.aistudy.assistant.assignment.entity.AssignmentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, Long> {

    List<AssignmentAttachment> findByAssignmentIdOrderByIdAsc(Long assignmentId);

    @Query("SELECT a.storedPath FROM AssignmentAttachment a WHERE a.assignment.id = :assignmentId")
    List<String> findStoredPathsByAssignmentId(@Param("assignmentId") Long assignmentId);
}
