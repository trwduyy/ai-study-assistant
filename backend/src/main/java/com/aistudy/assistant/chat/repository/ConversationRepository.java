package com.aistudy.assistant.chat.repository;

import com.aistudy.assistant.chat.entity.Conversation;
import com.aistudy.assistant.chat.entity.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    interface ActiveConversationRow {
        Long getConversationId();

        ConversationType getType();

        Long getClassId();

        String getClassName();

        String getOtherUserName();

        Instant getUpdatedAt();
    }

    Optional<Conversation> findByClassroomId(Long classId);

    Optional<Conversation> findByDirectKey(String directKey);

    @Query("""
            SELECT c.id AS conversationId,
                   c.type AS type,
                   cl.id AS classId,
                   cl.name AS className,
                   otherUser.fullName AS otherUserName,
                   c.updatedAt AS updatedAt
            FROM Conversation c
            JOIN ConversationMember myCm ON myCm.conversation.id = c.id
            LEFT JOIN c.classroom cl
            LEFT JOIN ConversationMember otherCm
                   ON otherCm.conversation.id = c.id
                  AND otherCm.user.id <> :userId
                  AND c.type = com.aistudy.assistant.chat.entity.ConversationType.DIRECT
            LEFT JOIN otherCm.user otherUser
            WHERE myCm.user.id = :userId
              AND myCm.leftAt IS NULL
            """)
    List<ActiveConversationRow> findActiveConversationRows(@Param("userId") Long userId);
}
