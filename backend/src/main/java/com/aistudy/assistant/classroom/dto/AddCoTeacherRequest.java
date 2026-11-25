package com.aistudy.assistant.classroom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for adding a co-teacher to a classroom.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCoTeacherRequest {

    @NotNull(message = "Mã người dùng giảng viên không được để trống.")
    private Long userId;
}
