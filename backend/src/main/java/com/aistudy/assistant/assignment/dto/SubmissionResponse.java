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
 * Detailed student submission DTO including uploaded files and grading outcome.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String studentName;
    private String textAnswer;
    private SubmissionStatus status;
    private Boolean late;
    private Boolean reopened;
    private Instant submittedAt;
    private BigDecimal score;
    private String feedback;
    private Long gradedBy;
    private Instant gradedAt;
    private List<SubmissionFileDto> files;
    private Integer version;
}
