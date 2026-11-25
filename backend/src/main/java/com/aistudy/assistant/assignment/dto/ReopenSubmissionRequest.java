package com.aistudy.assistant.assignment.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for reopening a submission so a student can submit again.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReopenSubmissionRequest {

    @Size(max = 1000, message = "Lý do mở lại bài nộp tối đa 1000 ký tự.")
    private String reason;

    private Integer version;
}
