package com.aistudy.assistant.identity.dto;

import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserResponse {

    private Long id;
    private String email;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private String avatarUrl;
    private Integer version;
}
