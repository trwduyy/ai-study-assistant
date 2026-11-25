package com.aistudy.assistant.chat.service;

import com.aistudy.assistant.chat.entity.Conversation;
import com.aistudy.assistant.chat.entity.ConversationMember;
import com.aistudy.assistant.chat.entity.ConversationType;
import com.aistudy.assistant.chat.repository.ConversationMemberRepository;
import com.aistudy.assistant.chat.repository.ConversationRepository;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.event.ClassMemberActivatedEvent;
import com.aistudy.assistant.classroom.event.ClassMemberDeactivatedEvent;
import com.aistudy.assistant.classroom.event.ClassroomCreatedEvent;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Synchronizes classroom lifecycle events with classroom group {@link Conversation} membership
 * inside the caller's active database transaction (BR-05).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomChatSyncService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void onClassroomCreated(ClassroomCreatedEvent event) {
        Conversation conversation = conversationRepository.findByClassroomId(event.classId())
                .orElseGet(() -> {
                    Classroom classroom = classroomRepository.getReferenceById(event.classId());
                    return conversationRepository.save(Conversation.builder()
                            .type(ConversationType.CLASSROOM)
                            .classroom(classroom)
                            .build());
                });

        upsertActiveMember(conversation, event.ownerId());
        log.debug("Provisioned classroom conversationId={} for classId={} with ownerId={}",
                conversation.getId(), event.classId(), event.ownerId());
    }

    @EventListener
    @Transactional
    public void onClassMemberActivated(ClassMemberActivatedEvent event) {
        Conversation conversation = conversationRepository.findByClassroomId(event.classId())
                .orElseGet(() -> {
                    Classroom classroom = classroomRepository.getReferenceById(event.classId());
                    return conversationRepository.save(Conversation.builder()
                            .type(ConversationType.CLASSROOM)
                            .classroom(classroom)
                            .build());
                });

        upsertActiveMember(conversation, event.userId());
        log.debug("Activated userId={} in classroom conversationId={} (classId={})",
                event.userId(), conversation.getId(), event.classId());
    }

    @EventListener
    @Transactional
    public void onClassMemberDeactivated(ClassMemberDeactivatedEvent event) {
        conversationRepository.findByClassroomId(event.classId()).ifPresent(conversation ->
                conversationMemberRepository.findByConversationIdAndUserId(conversation.getId(), event.userId())
                        .ifPresent(member -> {
                            if (member.getLeftAt() == null) {
                                member.setLeftAt(Instant.now());
                                conversationMemberRepository.save(member);
                                log.debug("Revoked chat access for userId={} in conversationId={} (classId={})",
                                        event.userId(), conversation.getId(), event.classId());
                            }
                        })
        );
    }

    private void upsertActiveMember(Conversation conversation, Long userId) {
        Instant now = Instant.now();
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversation.getId(), userId)
                .orElse(null);

        if (member != null) {
            if (member.getLeftAt() != null) {
                member.setLeftAt(null);
                member.setJoinedAt(now);
                conversationMemberRepository.save(member);
            }
        } else {
            User user = userRepository.getReferenceById(userId);
            conversationMemberRepository.save(ConversationMember.builder()
                    .conversation(conversation)
                    .user(user)
                    .joinedAt(now)
                    .leftAt(null)
                    .build());
        }
    }
}
