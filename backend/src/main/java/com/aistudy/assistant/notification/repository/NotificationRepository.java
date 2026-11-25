package com.aistudy.assistant.notification.repository;

import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Query("""
            SELECT new com.aistudy.assistant.notification.dto.NotificationResponse(
                n.id,
                n.type,
                n.title,
                n.body,
                n.targetType,
                n.targetId,
                n.readAt,
                n.createdAt
            )
            FROM Notification n
            WHERE n.user.id = :userId
              AND (:cursor IS NULL OR n.id < :cursor)
              AND (:unreadOnly IS NULL
                   OR (:unreadOnly = TRUE AND n.readAt IS NULL)
                   OR (:unreadOnly = FALSE AND n.readAt IS NOT NULL))
            ORDER BY n.id DESC
            """)
    List<NotificationResponse> findProjectedByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            @Param("unreadOnly") Boolean unreadOnly,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") Instant now);
}
