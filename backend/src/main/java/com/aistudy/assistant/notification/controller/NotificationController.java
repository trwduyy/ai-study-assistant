package com.aistudy.assistant.notification.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.dto.NotificationSettingDto;
import com.aistudy.assistant.notification.dto.NotificationUnreadCountResponse;
import com.aistudy.assistant.notification.dto.UpdateNotificationSettingsRequest;
import com.aistudy.assistant.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for in-app notifications (cursor-paginated), read status, and delivery preferences (FR-19).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications V2", description = "Thông báo thời gian thực và cấu hình kênh nhận thông báo")
@SecurityRequirement(name = "BearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Danh sách thông báo phân trang theo cursor")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", defaultValue = "30") int limit,
            @RequestParam(value = "unread", required = false) Boolean unread) {
        NotificationService.CursorPage<NotificationResponse> page =
                notificationService.getNotifications(cursor, limit, unread);
        return ResponseEntity.ok(
                ApiResponse.success(page.items(), PageMeta.ofCursor(page.items().size(), page.nextCursor()))
        );
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm số thông báo chưa đọc")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount()));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu đã đọc một thông báo")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAsRead(id)));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu đã đọc toàn bộ thông báo")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/settings")
    @Operation(summary = "Xem cấu hình bật/tắt thông báo IN_APP và EMAIL")
    public ResponseEntity<ApiResponse<List<NotificationSettingDto>>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getSettings()));
    }

    @PutMapping("/settings")
    @Operation(summary = "Cập nhật cấu hình thông báo IN_APP và EMAIL theo loại sự kiện")
    public ResponseEntity<ApiResponse<List<NotificationSettingDto>>> updateSettings(
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.updateSettings(request)));
    }
}
