package com.aistudy.assistant.assignment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request payload for updating a scheduled classroom test.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassTestRequest {

    @NotBlank(message = "Tiêu đề bài kiểm tra không được để trống.")
    @Size(max = 255, message = "Tiêu đề bài kiểm tra tối đa 255 ký tự.")
    private String title;

    @NotNull(message = "Thời gian bắt đầu không được để trống.")
    private Instant startAt;

    @NotNull(message = "Thời gian kết thúc không được để trống.")
    private Instant endAt;

    @NotNull(message = "Thời lượng làm bài không được để trống.")
    @Min(value = 1, message = "Thời lượng làm bài phải ít nhất 1 phút.")
    private Integer durationMinutes;

    @Min(value = 1, message = "Số lần làm tối đa phải ít nhất là 1.")
    private Integer maxAttempts;

    private Boolean shuffleQuestions;
}
