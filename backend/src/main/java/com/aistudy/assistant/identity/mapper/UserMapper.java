package com.aistudy.assistant.identity.mapper;

import com.aistudy.assistant.identity.dto.AuthUserResponse;
import com.aistudy.assistant.identity.dto.UserProfileResponse;
import com.aistudy.assistant.identity.entity.User;
import org.springframework.stereotype.Component;

/**
 * Maps {@link User} entities to API response DTOs.
 */
@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .version(user.getVersion())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public AuthUserResponse toAuthUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return AuthUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .version(user.getVersion())
                .build();
    }
}
