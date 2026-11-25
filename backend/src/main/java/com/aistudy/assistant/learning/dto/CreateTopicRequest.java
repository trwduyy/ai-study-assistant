package com.aistudy.assistant.learning.dto;

import jakarta.validation.constraints.Min;
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

    @NotBlank(message = "Tiêu đề chủ đề không được để trống")
    @Size(min = 1, max = 200, message = "Tiêu đề chủ đề phải từ 1 đến 200 ký tự")
    private String title;

    @Min(value = 0, message = "Thứ tự chủ đề phải >= 0")
    private Integer position;
}
