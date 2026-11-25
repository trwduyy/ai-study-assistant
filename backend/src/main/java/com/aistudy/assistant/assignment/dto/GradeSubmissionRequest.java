package com.aistudy.assistant.assignment.dto;

import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for grading and optionally returning a student submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeSubmissionRequest {

    @NotNull(message = "Điểm số không được để trống.")
    @DecimalMin(value = "0.00", message = "Điểm số không được âm.")
    private BigDecimal score;

    private String feedback;

    private SubmissionStatus status;

    private Integer version;
}
