package com.aistudy.assistant.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Notification delivery preference item per event type.
 */
public record NotificationSettingDto(
        @NotBlank(message = "Loại thông báo không được để trống.")
        @Size(max = 64, message = "Loại thông báo tối đa 64 ký tự.")
        String type,

        @NotNull(message = "Trạng thái kênh IN_APP không được để trống.")
        Boolean inAppEnabled,

        @NotNull(message = "Trạng thái kênh EMAIL không được để trống.")
        Boolean emailEnabled
) {
}
