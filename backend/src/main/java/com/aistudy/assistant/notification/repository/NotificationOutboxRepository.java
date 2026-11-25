package com.aistudy.assistant.notification.repository;

import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import com.aistudy.assistant.notification.entity.NotificationOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    boolean existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
            Long recipientId,
            String eventType,
            Long objectId,
            String reminderKey,
            NotificationChannel channel
    );

    @EntityGraph(attributePaths = "recipient")
    @Query("""
            SELECT o
            FROM NotificationOutbox o
            WHERE o.status = :status
              AND (o.nextTryAt IS NULL OR o.nextTryAt <= :now)
            ORDER BY o.createdAt ASC, o.id ASC
            """)
    List<NotificationOutbox> findPendingDueWithRecipient(
            @Param("status") NotificationOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "recipient")
    Optional<NotificationOutbox> findWithRecipientById(Long id);
}
