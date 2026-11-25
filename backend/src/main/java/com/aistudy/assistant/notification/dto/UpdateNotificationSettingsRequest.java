package com.aistudy.assistant.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload for updating per-event notification channel preferences.
 */
public record UpdateNotificationSettingsRequest(
        @NotEmpty(message = "Danh sách cấu hình thông báo không được để trống.")
        @Valid
        List<NotificationSettingDto> settings
) {
}
