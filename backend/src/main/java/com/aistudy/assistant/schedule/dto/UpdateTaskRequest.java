package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequest {

    private Long goalId;

    @NotBlank(message = "Tiêu đề nhiệm vụ không được để trống.")
    @Size(max = 255, message = "Tiêu đề nhiệm vụ tối đa 255 ký tự.")
    private String title;

    @NotNull(message = "Ngày thực hiện (taskDate) là bắt buộc.")
    private LocalDate taskDate;

    private GoalPriority priority;

    private TaskStatus status;

    private Instant remindAt;
}
