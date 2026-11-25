package com.aistudy.assistant.notification.service;

import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import com.aistudy.assistant.notification.entity.NotificationOutboxStatus;
import com.aistudy.assistant.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Records notification events into {@code notification_outbox} within the caller's active business transaction.
 */
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Transactional
    public void enqueueForAllChannels(
            User recipient,
            String eventType,
            String objectType,
            Long objectId,
            String reminderKey,
            Map<String, Object> payload) {
        String normalizedReminderKey = reminderKey != null ? reminderKey : "";
        Instant now = Instant.now();
        List<NotificationChannel> channels = List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        for (NotificationChannel channel : channels) {
            boolean exists = notificationOutboxRepository
                    .existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                            recipient.getId(),
                            eventType,
                            objectId,
                            normalizedReminderKey,
                            channel
                    );
            if (!exists) {
                NotificationOutbox entry = NotificationOutbox.builder()
                        .recipient(recipient)
                        .eventType(eventType)
                        .objectType(objectType)
                        .objectId(objectId)
                        .reminderKey(normalizedReminderKey)
                        .channel(channel)
                        .payload(payload)
                        .status(NotificationOutboxStatus.PENDING)
                        .attempts(0)
                        .nextTryAt(now)
                        .build();
                notificationOutboxRepository.save(entry);
            }
        }
    }
}
