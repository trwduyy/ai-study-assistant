package com.aistudy.assistant.identity.service.impl;

import com.aistudy.assistant.admin.service.AuditLogService;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.dto.ChangePasswordRequest;
import com.aistudy.assistant.identity.dto.UpdateProfileRequest;
import com.aistudy.assistant.identity.dto.UpdateUserRoleRequest;
import com.aistudy.assistant.identity.dto.UpdateUserStatusRequest;
import com.aistudy.assistant.identity.dto.UserProfileResponse;
import com.aistudy.assistant.identity.dto.UserSummaryResponse;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.mapper.UserMapper;
import com.aistudy.assistant.identity.repository.RefreshTokenRepository;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages authenticated user profiles, password changes, and Admin RBAC user governance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        Long currentUserId = SecurityUtils.currentUserId();
        User user = findUserById(currentUserId);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest request, Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thiếu thông tin version để cập nhật hồ sơ.",
                    List.of(new FieldErrorDetail("version", "Vui lòng cung cấp version trong body hoặc header If-Match"))
            );
        }

        User user = findUserById(currentUserId);
        verifyVersionMatches(user, expectedVersion);

        user.setFullName(request.getFullName().trim());
        user.setAvatarUrl(StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null);

        User updated = userRepository.saveAndFlush(user);
        log.info("Updated profile for user id={}, newVersion={}", currentUserId, updated.getVersion());
        return userMapper.toProfileResponse(updated);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        User user = findUserById(currentUserId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mật khẩu hiện tại không chính xác.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllActiveByUserId(currentUserId, Instant.now());
        log.info("Password changed and active refresh tokens revoked for user id={}", currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getUsers(UserRole role, UserStatus status, String keyword, Pageable pageable) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return userRepository.findAllProjected(role, status, normalizedKeyword, pageable);
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserRole(Long userId, UpdateUserRoleRequest request, Integer ifMatchVersion) {
        Long adminUserId = SecurityUtils.currentUserId();
        if (userId.equals(adminUserId) && request.getRole() != UserRole.ADMIN) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Quản trị viên không thể tự hạ vai trò của chính mình.");
        }

        User user = findUserById(userId);
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion != null) {
            verifyVersionMatches(user, expectedVersion);
        }

        UserRole previousRole = user.getRole();
        boolean roleChanged = previousRole != request.getRole();
        user.setRole(request.getRole());
        User updated = userRepository.saveAndFlush(user);

        if (roleChanged) {
            refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
        }
        auditLogService.record(
                adminUserId,
                "USER_ROLE_UPDATED",
                "USER",
                userId,
                Map.of("oldRole", previousRole.name(), "newRole", updated.getRole().name())
        );
        log.info("Admin id={} updated role of user id={} to {}", adminUserId, userId, request.getRole());
        return userMapper.toProfileResponse(updated);
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserStatus(Long userId, UpdateUserStatusRequest request, Integer ifMatchVersion) {
        Long adminUserId = SecurityUtils.currentUserId();
        if (userId.equals(adminUserId) && request.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Quản trị viên không thể tự khóa tài khoản của chính mình.");
        }

        User user = findUserById(userId);
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion != null) {
            verifyVersionMatches(user, expectedVersion);
        }

        UserStatus previousStatus = user.getStatus();
        user.setStatus(request.getStatus());
        if (request.getStatus() == UserStatus.ACTIVE) {
            user.resetFailedLogins();
        }
        User updated = userRepository.saveAndFlush(user);

        if (request.getStatus() == UserStatus.LOCKED) {
            refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
        }
        auditLogService.record(
                adminUserId,
                "USER_STATUS_UPDATED",
                "USER",
                userId,
                Map.of("oldStatus", previousStatus.name(), "newStatus", updated.getStatus().name())
        );
        log.info("Admin id={} updated status of user id={} to {}", adminUserId, userId, request.getStatus());
        return userMapper.toProfileResponse(updated);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void verifyVersionMatches(User user, Integer expectedVersion) {
        if (!Objects.equals(user.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }
    }
}
