package com.aistudy.assistant.notification.service;

import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import com.aistudy.assistant.notification.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationOutboxService outboxService;

    @Test
    @DisplayName("enqueue creates both IN_APP and EMAIL outbox rows when not already present")
    void enqueue_NewEvent_CreatesBothChannels() {
        User recipient = User.builder()
                .id(10L)
                .email("sv@actvn.edu.vn")
                .fullName("SV")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.getReferenceById(10L)).thenReturn(recipient);
        when(outboxRepository.existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                10L, "ASSIGNMENT_DUE_SOON", 401L, "DUE_24H", NotificationChannel.IN_APP)).thenReturn(false);
        when(outboxRepository.existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                10L, "ASSIGNMENT_DUE_SOON", 401L, "DUE_24H", NotificationChannel.EMAIL)).thenReturn(false);

        outboxService.enqueue(10L, "ASSIGNMENT_DUE_SOON", "ASSIGNMENT", 401L, "DUE_24H", Map.of("title", "Bai 1"));

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        List<NotificationChannel> savedChannels = captor.getAllValues().stream()
                .map(NotificationOutbox::getChannel)
                .toList();
        assertEquals(List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL), savedChannels);
    }

    @Test
    @DisplayName("enqueue skips duplicate entries when outbox dedup key already exists")
    void enqueue_DuplicateKeyExists_SkipsSave() {
        User recipient = User.builder().id(10L).email("sv@actvn.edu.vn").build();
        when(userRepository.getReferenceById(10L)).thenReturn(recipient);
        when(outboxRepository.existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                10L, "ASSIGNMENT_DUE_SOON", 401L, "DUE_24H", NotificationChannel.IN_APP)).thenReturn(true);
        when(outboxRepository.existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                10L, "ASSIGNMENT_DUE_SOON", 401L, "DUE_24H", NotificationChannel.EMAIL)).thenReturn(true);

        outboxService.enqueue(10L, "ASSIGNMENT_DUE_SOON", "ASSIGNMENT", 401L, "DUE_24H", Map.of());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("enqueue safely ignores DataIntegrityViolationException on concurrent duplicate insert")
    void enqueue_ConcurrentConstraintViolation_IgnoresSafely() {
        User recipient = User.builder().id(10L).email("sv@actvn.edu.vn").build();
        when(userRepository.getReferenceById(10L)).thenReturn(recipient);
        when(outboxRepository.existsByRecipientIdAndEventTypeAndObjectIdAndReminderKeyAndChannel(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(outboxRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_notification_outbox_dedup"));

        assertDoesNotThrow(() -> outboxService.enqueue(
                10L, "ASSIGNMENT_DUE_SOON", "ASSIGNMENT", 401L, "DUE_24H", Map.of()
        ));
    }
}
