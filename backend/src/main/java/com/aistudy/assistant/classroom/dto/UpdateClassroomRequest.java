package com.aistudy.assistant.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request payload for updating classroom metadata with optimistic locking version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassroomRequest {

    @NotBlank(message = "Tên lớp học không được để trống.")
    @Size(max = 200, message = "Tên lớp học tối đa 200 ký tự.")
    private String name;

    @Size(max = 5000, message = "Mô tả lớp học tối đa 5000 ký tự.")
    private String description;

    @Size(max = 150, message = "Tên môn học tối đa 150 ký tự.")
    private String subject;

    @Size(max = 500, message = "Đường dẫn ảnh đại diện tối đa 500 ký tự.")
    private String avatarUrl;

    private Instant startAt;

    private Instant endAt;

    private Integer version;
}
