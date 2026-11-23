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
public class CreateCourseRequest {

    @NotBlank(message = "Course title is required")
    @Size(min = 2, max = 200, message = "Course title must be between 2 and 200 characters")
    private String title;

    private String description;
}
