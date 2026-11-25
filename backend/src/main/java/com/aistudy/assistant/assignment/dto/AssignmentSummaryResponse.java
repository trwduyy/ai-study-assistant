package com.aistudy.assistant.assignment.dto;

import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projected assignment list item including caller's submission status without N+1 queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentSummaryResponse {

    private Long id;
    private Long classId;
    private String title;
    private BigDecimal maxScore;
    private Instant openAt;
    private Instant dueAt;
    private Boolean allowLate;
    private Integer maxSubmissions;
    private SubmissionStatus mySubmissionStatus;
    private Boolean mySubmissionLate;
    private Integer version;
    private Instant createdAt;
}
