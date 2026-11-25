package com.aistudy.assistant.identity.dto;

import com.aistudy.assistant.identity.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {

    @NotNull(message = "Trạng thái tài khoản không được để trống")
    private UserStatus status;

    private Integer version;
}
