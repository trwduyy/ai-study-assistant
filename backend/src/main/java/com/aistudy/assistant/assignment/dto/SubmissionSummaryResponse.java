package com.aistudy.assistant.assignment.dto;

import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projected submission summary item for teacher grading lists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionSummaryResponse {

    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private SubmissionStatus status;
    private Boolean late;
    private Instant submittedAt;
    private BigDecimal score;
    private Instant gradedAt;
    private Long fileCount;
    private Integer version;
}
