package com.aistudy.assistant.notification.service;

import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.dto.NotificationSettingDto;
import com.aistudy.assistant.notification.dto.NotificationUnreadCountResponse;
import com.aistudy.assistant.notification.dto.UpdateNotificationSettingsRequest;

import java.util.List;

public interface NotificationService {

    CursorPage<NotificationResponse> getNotifications(Long cursor, int limit, Boolean unread);

    NotificationUnreadCountResponse getUnreadCount();

    NotificationResponse markAsRead(Long notificationId);

    void markAllAsRead();

    List<NotificationSettingDto> getSettings();

    List<NotificationSettingDto> updateSettings(UpdateNotificationSettingsRequest request);

    record CursorPage<T>(List<T> items, String nextCursor) {
    }
}
