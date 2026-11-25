package com.aistudy.assistant.notification.worker;

import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.entity.Notification;
import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import com.aistudy.assistant.notification.entity.NotificationOutboxStatus;
import com.aistudy.assistant.notification.entity.NotificationSetting;
import com.aistudy.assistant.notification.mapper.NotificationMapper;
import com.aistudy.assistant.notification.repository.NotificationOutboxRepository;
import com.aistudy.assistant.notification.repository.NotificationRepository;
import com.aistudy.assistant.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Short-lived transactional persistence helper for {@link NotificationWorker}.
 * Ensures SMTP network calls always execute outside database transactions.
 */
@Service
@RequiredArgsConstructor
public class NotificationOutboxTxService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_SECONDS = 30L;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final NotificationMapper notificationMapper;

    public record OutboxWorkItem(
            Long outboxId,
            Long recipientId,
            String recipientEmail,
            String eventType,
            String objectType,
            Long objectId,
            NotificationChannel channel,
            Map<String, Object> payload
    ) {
    }

    @Transactional(readOnly = true)
    public List<OutboxWorkItem> loadDuePendingBatch(int batchSize) {
        List<NotificationOutbox> items = outboxRepository.findPendingDueWithRecipient(
                NotificationOutboxStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, batchSize)
        );
        return items.stream()
                .map(item -> new OutboxWorkItem(
                        item.getId(),
                        item.getRecipient().getId(),
                        item.getRecipient().getEmail(),
                        item.getEventType(),
                        item.getObjectType(),
                        item.getObjectId(),
                        item.getChannel(),
                        item.getPayload() != null ? new LinkedHashMap<>(item.getPayload()) : Map.of()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isChannelEnabled(Long userId, String eventType, NotificationChannel channel) {
        Optional<NotificationSetting> settingOpt = settingRepository.findByUserIdAndType(userId, eventType);
        if (settingOpt.isEmpty()) {
            return true;
        }
        NotificationSetting setting = settingOpt.get();
        return channel == NotificationChannel.IN_APP
                ? !Boolean.FALSE.equals(setting.getInAppEnabled())
                : !Boolean.FALSE.equals(setting.getEmailEnabled());
    }

    @Transactional
    public void markSkipped(Long outboxId) {
        outboxRepository.findById(outboxId).ifPresent(entry -> {
            if (entry.getStatus() == NotificationOutboxStatus.PENDING) {
                entry.setStatus(NotificationOutboxStatus.SKIPPED);
                outboxRepository.save(entry);
            }
        });
    }

    @Transactional
    public Optional<NotificationResponse> deliverInAppAndMarkSent(Long outboxId, String title, String body) {
        Optional<NotificationOutbox> entryOpt = outboxRepository.findWithRecipientById(outboxId);
        if (entryOpt.isEmpty()) {
            return Optional.empty();
        }
        NotificationOutbox entry = entryOpt.get();
        if (entry.getStatus() != NotificationOutboxStatus.PENDING) {
            return Optional.empty();
        }

        Notification notification = Notification.builder()
                .user(entry.getRecipient())
                .type(entry.getEventType())
                .title(title)
                .body(body)
                .targetType(entry.getObjectType())
                .targetId(entry.getObjectId())
                .build();
        Notification savedNotification = notificationRepository.save(notification);

        entry.setStatus(NotificationOutboxStatus.SENT);
        entry.setSentAt(Instant.now());
        entry.setLastError(null);
        outboxRepository.save(entry);

        return Optional.of(notificationMapper.toNotificationResponse(savedNotification));
    }

    @Transactional
    public void markSent(Long outboxId) {
        outboxRepository.findById(outboxId).ifPresent(entry -> {
            if (entry.getStatus() == NotificationOutboxStatus.PENDING) {
                entry.setStatus(NotificationOutboxStatus.SENT);
                entry.setSentAt(Instant.now());
                entry.setLastError(null);
                outboxRepository.save(entry);
            }
        });
    }

    @Transactional
    public void recordEmailFailure(Long outboxId, String errorMessage) {
        outboxRepository.findById(outboxId).ifPresent(entry -> {
            if (entry.getStatus() != NotificationOutboxStatus.PENDING) {
                return;
            }
            int nextAttempts = (entry.getAttempts() != null ? entry.getAttempts() : 0) + 1;
            entry.setAttempts(nextAttempts);
            entry.setLastError(truncateError(errorMessage));

            if (nextAttempts >= MAX_RETRY_ATTEMPTS) {
                entry.setStatus(NotificationOutboxStatus.FAILED);
            } else {
                long backoffSeconds = BASE_BACKOFF_SECONDS * (1L << (nextAttempts - 1));
                entry.setStatus(NotificationOutboxStatus.PENDING);
                entry.setNextTryAt(Instant.now().plusSeconds(backoffSeconds));
            }
            outboxRepository.save(entry);
        });
    }

    private String truncateError(String error) {
        if (error == null) {
            return "Unknown email delivery failure";
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
