package com.aistudy.assistant.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for submitting a classroom join request by invite code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJoinRequestRequest {

    @NotBlank(message = "Mã lớp học không được để trống.")
    @Size(max = 20, message = "Mã lớp học tối đa 20 ký tự.")
    private String code;
}
