package com.aistudy.assistant.notification.repository;

import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    boolean existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
            Long recipientId,
            String eventType,
            Long objectId,
            String reminderKey,
            NotificationChannel channel
    );
}
