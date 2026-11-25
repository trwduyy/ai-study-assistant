package com.aistudy.assistant.ai.repository;

import com.aistudy.assistant.ai.entity.AiChatMessage;
import com.aistudy.assistant.ai.entity.AiMessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    long countByChatUserIdAndRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId,
            AiMessageRole role,
            Instant startInclusive,
            Instant endExclusive
    );

    @Query("""
            SELECT m
            FROM AiChatMessage m
            WHERE m.chat.id = :chatId
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<AiChatMessage> findRecentMessages(
            @Param("chatId") Long chatId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    List<AiChatMessage> findTop6ByChatIdOrderByIdDesc(Long chatId);

    @Query("SELECT m FROM AiChatMessage m JOIN FETCH m.chat WHERE m.id = :id")
    Optional<AiChatMessage> findWithChatById(@Param("id") Long id);
}
