package com.aistudy.assistant.assignment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request payload for creating a classroom assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssignmentRequest {

    @NotBlank(message = "Tiêu đề bài tập không được để trống.")
    @Size(max = 255, message = "Tiêu đề bài tập tối đa 255 ký tự.")
    private String title;

    private String instructions;

    @DecimalMin(value = "0.01", message = "Điểm tối đa phải lớn hơn 0.")
    private BigDecimal maxScore;

    private Instant openAt;

    private Instant dueAt;

    private Boolean allowLate;

    @Min(value = 1, message = "Số lần nộp tối đa phải ít nhất là 1.")
    private Integer maxSubmissions;
}
