package com.aistudy.assistant.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTopicRequest {

    @NotBlank(message = "Topic title is required")
    @Size(min = 1, max = 200, message = "Topic title must be between 1 and 200 characters")
    private String title;

    @Builder.Default
    private Integer orderIndex = 0;
}
