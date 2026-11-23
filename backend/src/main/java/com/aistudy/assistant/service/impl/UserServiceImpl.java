package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.common.SecurityUtils;
import com.aistudy.assistant.dto.auth.ChangePasswordRequest;
import com.aistudy.assistant.dto.user.UpdateProfileRequest;
import com.aistudy.assistant.dto.user.UserProfileResponse;
import com.aistudy.assistant.entity.User;
import com.aistudy.assistant.exception.BadRequestException;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.UserRepository;
import com.aistudy.assistant.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        return UserProfileResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password does not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user id: {}", currentUserId);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        user.setFullName(request.getFullName().trim());
        User updatedUser = userRepository.save(user);
        log.info("Profile updated for user id: {}", currentUserId);

        return UserProfileResponse.fromEntity(updatedUser);
    }
}
