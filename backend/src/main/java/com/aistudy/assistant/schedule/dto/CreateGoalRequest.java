package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.GoalPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoalRequest {

    @NotBlank(message = "Tiêu đề mục tiêu không được để trống.")
    @Size(max = 255, message = "Tiêu đề mục tiêu tối đa 255 ký tự.")
    private String title;

    @Size(max = 128, message = "Môn học tối đa 128 ký tự.")
    private String subject;

    private LocalDate dueDate;

    private GoalPriority priority;

    private Map<String, Object> freeTime;
}
