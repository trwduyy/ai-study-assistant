package com.aistudy.assistant.assignment.dto;

import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detailed assignment DTO including attachments and optional caller submission status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private Long id;
    private Long classId;
    private Long creatorId;
    private String title;
    private String instructions;
    private BigDecimal maxScore;
    private Instant openAt;
    private Instant dueAt;
    private Boolean allowLate;
    private Integer maxSubmissions;
    private List<AssignmentAttachmentDto> attachments;
    private Long mySubmissionId;
    private SubmissionStatus mySubmissionStatus;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
