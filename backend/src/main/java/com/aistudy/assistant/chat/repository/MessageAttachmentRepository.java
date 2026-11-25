package com.aistudy.assistant.chat.repository;

import com.aistudy.assistant.chat.entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
}
