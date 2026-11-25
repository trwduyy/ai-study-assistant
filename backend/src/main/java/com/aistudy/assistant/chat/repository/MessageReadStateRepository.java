package com.aistudy.assistant.chat.repository;

import com.aistudy.assistant.chat.entity.MessageReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageReadStateRepository extends JpaRepository<MessageReadState, Long> {

    Optional<MessageReadState> findByConversationIdAndUserId(Long conversationId, Long userId);
}
