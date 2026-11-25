package com.aistudy.assistant.learning.dto;

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
public class UpdateCourseRequest {

    @NotBlank(message = "Tên khóa học không được để trống")
    @Size(min = 2, max = 200, message = "Tên khóa học phải từ 2 đến 200 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả khóa học tối đa 5000 ký tự")
    private String description;

    private Integer version;
}
