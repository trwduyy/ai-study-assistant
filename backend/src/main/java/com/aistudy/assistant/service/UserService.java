package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.auth.ChangePasswordRequest;
import com.aistudy.assistant.dto.user.UpdateProfileRequest;
import com.aistudy.assistant.dto.user.UserProfileResponse;

public interface UserService {

    UserProfileResponse getCurrentUserProfile();

    void changePassword(ChangePasswordRequest request);

    UserProfileResponse updateProfile(UpdateProfileRequest request);
}
