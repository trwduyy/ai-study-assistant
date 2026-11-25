package com.aistudy.assistant.chat.repository;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    interface ConversationUnreadCount {
        Long getConversationId();

        Long getUnreadCount();
    }

    @EntityGraph(attributePaths = {"conversation", "sender"})
    Optional<Message> findWithDetailsByConversationIdAndSenderIdAndClientMessageId(
            Long conversationId,
            Long senderId,
            String clientMessageId
    );

    boolean existsByIdAndConversationId(Long id, Long conversationId);

    @Query("""
            SELECT new com.aistudy.assistant.chat.dto.ChatMessageResponse(
                m.id,
                m.conversation.id,
                s.id,
                s.fullName,
                m.content,
                m.status,
                m.clientMessageId,
                m.createdAt
            )
            FROM Message m
            JOIN m.sender s
            WHERE m.conversation.id = :conversationId
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<ChatMessageResponse> findProjectedByConversationIdWithCursor(
            @Param("conversationId") Long conversationId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM Message m
            JOIN FETCH m.sender
            WHERE m.id IN (
                SELECT MAX(m2.id)
                FROM Message m2
                WHERE m2.conversation.id IN :conversationIds
                GROUP BY m2.conversation.id
            )
            """)
    List<Message> findLatestMessagesByConversationIds(@Param("conversationIds") Collection<Long> conversationIds);

    @Query("""
            SELECT m.conversation.id AS conversationId,
                   COUNT(m) AS unreadCount
            FROM Message m
            LEFT JOIN MessageReadState mrs
                   ON mrs.conversation.id = m.conversation.id
                  AND mrs.user.id = :userId
            WHERE m.conversation.id IN :conversationIds
              AND m.sender.id <> :userId
              AND (mrs.lastReadMessageId IS NULL OR m.id > mrs.lastReadMessageId)
            GROUP BY m.conversation.id
            """)
    List<ConversationUnreadCount> countUnreadByConversationIds(
            @Param("userId") Long userId,
            @Param("conversationIds") Collection<Long> conversationIds
    );
}
