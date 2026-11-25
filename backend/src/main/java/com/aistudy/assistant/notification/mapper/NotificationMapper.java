package com.aistudy.assistant.notification.mapper;

import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.entity.Notification;
import org.springframework.stereotype.Component;

/**
 * Maps {@code notification} entities to REST and WebSocket DTOs.
 */
@Component
public class NotificationMapper {

    public NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
