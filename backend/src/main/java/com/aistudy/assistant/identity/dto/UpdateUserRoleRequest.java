package com.aistudy.assistant.identity.dto;

import com.aistudy.assistant.identity.entity.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRoleRequest {

    @NotNull(message = "Vai trò không được để trống")
    private UserRole role;

    private Integer version;
}
