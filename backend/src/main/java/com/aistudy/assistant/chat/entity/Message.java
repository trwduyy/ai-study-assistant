package com.aistudy.assistant.chat.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
import com.aistudy.assistant.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Persisted chat message mapped to {@code messages} with idempotent {@code client_message_id}.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status = MessageStatus.SENT;

    @Column(name = "client_message_id", nullable = false, length = 64)
    private String clientMessageId;

    @PrePersist
    protected void ensureDefaults() {
        if (this.status == null) {
            this.status = MessageStatus.SENT;
        }
    }
}
