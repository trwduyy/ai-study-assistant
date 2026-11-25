package com.aistudy.assistant.notification.service;

import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import com.aistudy.assistant.notification.entity.NotificationOutboxStatus;
import com.aistudy.assistant.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Records notification events into {@code notification_outbox} within the caller's active business transaction,
 * automatically creating both {@code IN_APP} and {@code EMAIL} channels with deduplication protection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final UserRepository userRepository;

    @Transactional
    public void enqueue(
            Long recipientId,
            String eventType,
            String objectType,
            Long objectId,
            String reminderKey,
            Map<String, Object> payload) {
        User recipient = userRepository.getReferenceById(recipientId);
        enqueueInternal(recipient, recipientId, eventType, objectType, objectId, reminderKey, payload);
    }

    @Transactional
    public void enqueueForAllChannels(
            User recipient,
            String eventType,
            String objectType,
            Long objectId,
            String reminderKey,
            Map<String, Object> payload) {
        enqueueInternal(recipient, recipient.getId(), eventType, objectType, objectId, reminderKey, payload);
    }

    private void enqueueInternal(
            User recipient,
            Long recipientId,
            String eventType,
            String objectType,
            Long objectId,
            String reminderKey,
            Map<String, Object> payload) {
        String normalizedReminderKey = reminderKey != null ? reminderKey : "";
        Map<String, Object> safePayload = payload != null ? payload : Map.of();
        Instant now = Instant.now();

        for (NotificationChannel channel : List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)) {
            boolean exists = notificationOutboxRepository
                    .existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                            recipientId,
                            eventType,
                            objectId,
                            normalizedReminderKey,
                            channel
                    );
            if (exists) {
                continue;
            }
            NotificationOutbox entry = NotificationOutbox.builder()
                    .recipient(recipient)
                    .eventType(eventType)
                    .objectType(objectType)
                    .objectId(objectId)
                    .reminderKey(normalizedReminderKey)
                    .channel(channel)
                    .payload(safePayload)
                    .status(NotificationOutboxStatus.PENDING)
                    .attempts(0)
                    .nextTryAt(now)
                    .build();
            try {
                notificationOutboxRepository.save(entry);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Ignoring duplicate outbox entry recipientId={} eventType={} objectId={} channel={}",
                        recipientId, eventType, objectId, channel);
            }
        }
    }
}
