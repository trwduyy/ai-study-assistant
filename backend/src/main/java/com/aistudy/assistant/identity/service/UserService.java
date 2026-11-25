package com.aistudy.assistant.identity.service;

import com.aistudy.assistant.identity.dto.ChangePasswordRequest;
import com.aistudy.assistant.identity.dto.UpdateProfileRequest;
import com.aistudy.assistant.identity.dto.UpdateUserRoleRequest;
import com.aistudy.assistant.identity.dto.UpdateUserStatusRequest;
import com.aistudy.assistant.identity.dto.UserProfileResponse;
import com.aistudy.assistant.identity.dto.UserSummaryResponse;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest request, Integer ifMatchVersion);

    void changePassword(ChangePasswordRequest request);

    Page<UserSummaryResponse> getUsers(UserRole role, UserStatus status, String keyword, Pageable pageable);

    UserProfileResponse updateUserRole(Long userId, UpdateUserRoleRequest request, Integer ifMatchVersion);

    UserProfileResponse updateUserStatus(Long userId, UpdateUserStatusRequest request, Integer ifMatchVersion);
}
