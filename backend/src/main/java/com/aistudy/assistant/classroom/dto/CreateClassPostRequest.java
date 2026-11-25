package com.aistudy.assistant.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for publishing a new announcement post in a classroom.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClassPostRequest {

    @NotBlank(message = "Tiêu đề bài đăng không được để trống.")
    @Size(max = 255, message = "Tiêu đề bài đăng tối đa 255 ký tự.")
    private String title;

    @NotBlank(message = "Nội dung bài đăng không được để trống.")
    private String content;

    private Boolean pinned;
}
