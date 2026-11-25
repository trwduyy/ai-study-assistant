package com.aistudy.assistant.identity.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.dto.ChangePasswordRequest;
import com.aistudy.assistant.identity.dto.UpdateProfileRequest;
import com.aistudy.assistant.identity.dto.UpdateUserRoleRequest;
import com.aistudy.assistant.identity.dto.UpdateUserStatusRequest;
import com.aistudy.assistant.identity.dto.UserProfileResponse;
import com.aistudy.assistant.identity.dto.UserSummaryResponse;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for self-service profile management and Admin RBAC user administration.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users & RBAC V2", description = "Quản lý hồ sơ cá nhân và phân quyền người dùng (Admin)")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Xem hồ sơ người dùng đang đăng nhập")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser() {
        UserProfileResponse response = userService.getCurrentUserProfile();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    @Operation(summary = "Cập nhật hồ sơ cá nhân (kiểm tra version hoặc header If-Match)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        UserProfileResponse response = userService.updateCurrentUserProfile(request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu người dùng đang đăng nhập")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách người dùng có lọc và phân trang (Admin)")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUsers(
            @RequestParam(value = "role", required = false) UserRole role,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserSummaryResponse> page = userService.getUsers(role, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thay đổi vai trò người dùng (Admin)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        UserProfileResponse response = userService.updateUserRole(id, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Khóa hoặc mở khóa tài khoản người dùng (Admin)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        UserProfileResponse response = userService.updateUserStatus(id, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Integer parseIfMatchVersion(String ifMatchHeader) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            return null;
        }
        String sanitized = ifMatchHeader.trim();
        if (sanitized.startsWith("W/")) {
            sanitized = sanitized.substring(2).trim();
        }
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() >= 2) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        try {
            return Integer.parseInt(sanitized);
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header If-Match không hợp lệ.");
        }
    }
}
