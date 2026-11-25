package com.aistudy.assistant.notification.worker;

import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.entity.NotificationOutbox;
import com.aistudy.assistant.notification.entity.NotificationOutboxStatus;
import com.aistudy.assistant.notification.integration.EmailService;
import com.aistudy.assistant.notification.mapper.NotificationMapper;
import com.aistudy.assistant.notification.repository.NotificationOutboxRepository;
import com.aistudy.assistant.notification.repository.NotificationRepository;
import com.aistudy.assistant.notification.repository.NotificationSettingRepository;
import com.aistudy.assistant.notification.worker.NotificationOutboxTxService.OutboxWorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWorkerTest {

    @Mock
    private NotificationOutboxTxService txService;

    @Mock
    private EmailService emailService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSettingRepository settingRepository;

    private NotificationWorker worker;
    private NotificationOutboxTxService realTxService;

    @BeforeEach
    void setUp() {
        worker = new NotificationWorker(txService, emailService, messagingTemplate);
        realTxService = new NotificationOutboxTxService(
                outboxRepository,
                notificationRepository,
                settingRepository,
                new NotificationMapper()
        );
    }

    @Test
    @DisplayName("processSingleItem marks outbox SKIPPED when user disabled channel in settings")
    void processSingleItem_DisabledChannel_MarksSkipped() {
        OutboxWorkItem item = new OutboxWorkItem(
                100L,
                10L,
                "sv@actvn.edu.vn",
                "ASSIGNMENT_DUE_SOON",
                "ASSIGNMENT",
                401L,
                NotificationChannel.EMAIL,
                Map.of("title", "Socket TCP")
        );

        when(txService.isChannelEnabled(10L, "ASSIGNMENT_DUE_SOON", NotificationChannel.EMAIL)).thenReturn(false);

        worker.processSingleItem(item);

        verify(txService).markSkipped(100L);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        verify(txService, never()).markSent(any());
    }

    @Test
    @DisplayName("processSingleItem pushes IN_APP notification to /user/queue/notifications")
    void processSingleItem_InApp_PushesToWebSocket() {
        OutboxWorkItem item = new OutboxWorkItem(
                101L,
                10L,
                "sv@actvn.edu.vn",
                "ASSIGNMENT_GRADED",
                "SUBMISSION",
                990L,
                NotificationChannel.IN_APP,
                Map.of("assignmentTitle", "Socket TCP", "score", "9.50")
        );
        NotificationResponse dto = new NotificationResponse(
                801L, "ASSIGNMENT_GRADED", "Bài tập đã được chấm điểm", "Body", "SUBMISSION", 990L, null, Instant.now()
        );

        when(txService.isChannelEnabled(10L, "ASSIGNMENT_GRADED", NotificationChannel.IN_APP)).thenReturn(true);
        when(txService.deliverInAppAndMarkSent(eq(101L), anyString(), anyString())).thenReturn(Optional.of(dto));

        worker.processSingleItem(item);

        verify(messagingTemplate).convertAndSendToUser("10", "/queue/notifications", dto);
    }

    @Test
    @DisplayName("processSingleItem records retry with backoff on email failure and marks FAILED after 3 attempts")
    void processSingleItem_EmailFailure_RetriesThenFails() {
        OutboxWorkItem item = new OutboxWorkItem(
                102L,
                10L,
                "sv@actvn.edu.vn",
                "ASSIGNMENT_DUE_SOON",
                "ASSIGNMENT",
                401L,
                NotificationChannel.EMAIL,
                Map.of("title", "Socket TCP", "className", "Lập trình Mạng")
        );

        when(txService.isChannelEnabled(10L, "ASSIGNMENT_DUE_SOON", NotificationChannel.EMAIL)).thenReturn(true);
        doThrow(new RuntimeException("SMTP timeout")).when(emailService).sendEmail(anyString(), anyString(), anyString());

        worker.processSingleItem(item);

        verify(txService).recordEmailFailure(102L, "SMTP timeout");
        verify(txService, never()).markSent(102L);

        // Verify backoff & terminal FAILED transition in NotificationOutboxTxService
        User user = User.builder().id(10L).email("sv@actvn.edu.vn").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build();
        NotificationOutbox entry = NotificationOutbox.builder()
                .id(102L)
                .recipient(user)
                .eventType("ASSIGNMENT_DUE_SOON")
                .objectType("ASSIGNMENT")
                .objectId(401L)
                .channel(NotificationChannel.EMAIL)
                .payload(Map.of())
                .status(NotificationOutboxStatus.PENDING)
                .attempts(0)
                .nextTryAt(Instant.now())
                .build();
        when(outboxRepository.findById(102L)).thenReturn(Optional.of(entry));

        Instant beforeFirstFail = Instant.now();
        realTxService.recordEmailFailure(102L, "SMTP timeout");
        assertEquals(1, entry.getAttempts());
        assertEquals(NotificationOutboxStatus.PENDING, entry.getStatus());
        assertTrue(entry.getNextTryAt().isAfter(beforeFirstFail.plusSeconds(25)));

        realTxService.recordEmailFailure(102L, "SMTP timeout 2");
        assertEquals(2, entry.getAttempts());
        assertEquals(NotificationOutboxStatus.PENDING, entry.getStatus());

        realTxService.recordEmailFailure(102L, "SMTP timeout 3");
        assertEquals(3, entry.getAttempts());
        assertEquals(NotificationOutboxStatus.FAILED, entry.getStatus());
        assertNotNull(entry.getLastError());
    }
}
