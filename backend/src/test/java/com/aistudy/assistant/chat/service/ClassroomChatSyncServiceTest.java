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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomChatSyncServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClassroomChatSyncService syncService;

    @Test
    @DisplayName("onClassroomCreated creates CLASSROOM conversation and adds owner member")
    void onClassroomCreated_ProvisionsRoomAndOwner() {
        Classroom classroom = Classroom.builder().id(100L).name("Mạng máy tính").build();
        User owner = User.builder().id(1L).fullName("GV").build();

        when(conversationRepository.findByClassroomId(100L)).thenReturn(Optional.empty());
        when(classroomRepository.getReferenceById(100L)).thenReturn(classroom);
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(15L);
            return c;
        });
        when(conversationMemberRepository.findByConversationIdAndUserId(15L, 1L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(owner);

        syncService.onClassroomCreated(new ClassroomCreatedEvent(100L, 1L));

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        assertEquals(ConversationType.CLASSROOM, convCaptor.getValue().getType());

        ArgumentCaptor<ConversationMember> memberCaptor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(conversationMemberRepository).save(memberCaptor.capture());
        assertEquals(1L, memberCaptor.getValue().getUser().getId());
        assertNull(memberCaptor.getValue().getLeftAt());
    }

    @Test
    @DisplayName("onClassMemberActivated clears leftAt for rejoining member and onClassMemberDeactivated sets leftAt")
    void memberLifecycle_SyncsLeftAtCorrectly() {
        Conversation conv = Conversation.builder().id(15L).type(ConversationType.CLASSROOM).build();
        User student = User.builder().id(10L).build();
        ConversationMember member = ConversationMember.builder()
                .id(50L)
                .conversation(conv)
                .user(student)
                .joinedAt(Instant.now().minusSeconds(1000))
                .leftAt(null)
                .build();

        when(conversationRepository.findByClassroomId(100L)).thenReturn(Optional.of(conv));
        when(conversationMemberRepository.findByConversationIdAndUserId(15L, 10L)).thenReturn(Optional.of(member));

        // Leave / remove -> sets leftAt != null
        syncService.onClassMemberDeactivated(new ClassMemberDeactivatedEvent(100L, 10L));
        assertNotNull(member.getLeftAt());

        // Re-approved -> clears leftAt = null
        syncService.onClassMemberActivated(new ClassMemberActivatedEvent(100L, 10L));
        assertNull(member.getLeftAt());
    }
}
