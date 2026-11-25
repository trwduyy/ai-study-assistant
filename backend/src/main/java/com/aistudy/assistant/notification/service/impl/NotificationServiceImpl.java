package com.aistudy.assistant.notification.service.impl;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.dto.NotificationSettingDto;
import com.aistudy.assistant.notification.dto.NotificationUnreadCountResponse;
import com.aistudy.assistant.notification.dto.UpdateNotificationSettingsRequest;
import com.aistudy.assistant.notification.entity.Notification;
import com.aistudy.assistant.notification.entity.NotificationSetting;
import com.aistudy.assistant.notification.mapper.NotificationMapper;
import com.aistudy.assistant.notification.repository.NotificationRepository;
import com.aistudy.assistant.notification.repository.NotificationSettingRepository;
import com.aistudy.assistant.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for cursor-paginated notification listing, read-state updates, and user delivery preferences.
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;
    private static final List<String> DEFAULT_EVENT_TYPES = List.of(
            "CLASS_JOIN_REQUEST_CREATED",
            "CLASS_JOIN_REQUEST_APPROVED",
            "CLASS_JOIN_REQUEST_REJECTED",
            "CLASS_CO_TEACHER_ADDED",
            "ASSIGNMENT_CREATED",
            "ASSIGNMENT_SUBMITTED",
            "ASSIGNMENT_GRADED",
            "ASSIGNMENT_REOPENED",
            "ASSIGNMENT_DUE_SOON",
            "CLASS_TEST_CREATED",
            "CLASS_EVENT_CREATED",
            "CLASS_EVENT_UPDATED",
            "CLASS_EVENT_CANCELLED",
            "EVENT_REMINDER_30M"
    );

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> getNotifications(Long cursor, int limit, Boolean unread) {
        Long userId = SecurityUtils.currentUserId();
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<NotificationResponse> rows = notificationRepository.findProjectedByUserIdWithCursor(
                userId,
                cursor,
                unread,
                PageRequest.of(0, safeLimit + 1)
        );

        boolean hasMore = rows.size() > safeLimit;
        List<NotificationResponse> items = hasMore ? rows.subList(0, safeLimit) : rows;
        String nextCursor = hasMore && !items.isEmpty()
                ? String.valueOf(items.get(items.size() - 1).id())
                : null;
        return new CursorPage<>(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount() {
        Long userId = SecurityUtils.currentUserId();
        return new NotificationUnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = SecurityUtils.currentUserId();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return notificationMapper.toNotificationResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        Long userId = SecurityUtils.currentUserId();
        notificationRepository.markAllAsRead(userId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationSettingDto> getSettings() {
        Long userId = SecurityUtils.currentUserId();
        return mergeWithDefaults(notificationSettingRepository.findByUserIdOrderByTypeAsc(userId));
    }

    @Override
    @Transactional
    public List<NotificationSettingDto> updateSettings(UpdateNotificationSettingsRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.getReferenceById(userId);

        Map<String, NotificationSetting> existingByType = notificationSettingRepository
                .findByUserIdOrderByTypeAsc(userId)
                .stream()
                .collect(Collectors.toMap(NotificationSetting::getType, s -> s));

        List<NotificationSetting> toSave = new ArrayList<>();
        for (NotificationSettingDto item : request.settings()) {
            String normalizedType = item.type().trim().toUpperCase(Locale.ROOT);
            NotificationSetting setting = existingByType.getOrDefault(
                    normalizedType,
                    NotificationSetting.builder()
                            .user(user)
                            .type(normalizedType)
                            .build()
            );
            setting.setInAppEnabled(item.inAppEnabled());
            setting.setEmailEnabled(item.emailEnabled());
            toSave.add(setting);
            existingByType.put(normalizedType, setting);
        }

        notificationSettingRepository.saveAll(toSave);
        return mergeWithDefaults(new ArrayList<>(existingByType.values()));
    }

    private List<NotificationSettingDto> mergeWithDefaults(List<NotificationSetting> savedSettings) {
        Map<String, NotificationSettingDto> merged = new LinkedHashMap<>();
        for (String defaultType : DEFAULT_EVENT_TYPES) {
            merged.put(defaultType, new NotificationSettingDto(defaultType, true, true));
        }
        for (NotificationSetting saved : savedSettings) {
            merged.put(saved.getType(), new NotificationSettingDto(
                    saved.getType(),
                    !Boolean.FALSE.equals(saved.getInAppEnabled()),
                    !Boolean.FALSE.equals(saved.getEmailEnabled())
            ));
        }
        return List.copyOf(merged.values());
    }
}
