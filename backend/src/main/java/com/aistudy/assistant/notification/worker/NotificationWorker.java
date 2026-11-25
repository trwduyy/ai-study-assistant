package com.aistudy.assistant.notification.worker;

import com.aistudy.assistant.notification.dto.NotificationResponse;
import com.aistudy.assistant.notification.entity.NotificationChannel;
import com.aistudy.assistant.notification.integration.EmailService;
import com.aistudy.assistant.notification.worker.NotificationOutboxTxService.OutboxWorkItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduled background worker polling {@code notification_outbox} every 5 seconds.
 * Dispatches {@code IN_APP} alerts via WebSocket STOMP and sends {@code EMAIL} outside DB transactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWorker {

    private static final int BATCH_SIZE = 50;
    private static final String USER_NOTIFICATIONS_DESTINATION = "/queue/notifications";

    private final NotificationOutboxTxService txService;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelayString = "${app.notification.worker-poll-ms:5000}")
    public void processPendingOutbox() {
        List<OutboxWorkItem> batch = txService.loadDuePendingBatch(BATCH_SIZE);
        for (OutboxWorkItem item : batch) {
            processSingleItem(item);
        }
    }

    public void processSingleItem(OutboxWorkItem item) {
        try {
            if (!txService.isChannelEnabled(item.recipientId(), item.eventType(), item.channel())) {
                txService.markSkipped(item.outboxId());
                return;
            }

            NotificationText text = formatNotificationText(item.eventType(), item.payload());

            if (item.channel() == NotificationChannel.IN_APP) {
                Optional<NotificationResponse> savedOpt = txService.deliverInAppAndMarkSent(
                        item.outboxId(),
                        text.title(),
                        text.body()
                );
                savedOpt.ifPresent(dto -> pushRealtimeNotification(item.recipientId(), dto));
            } else if (item.channel() == NotificationChannel.EMAIL) {
                try {
                    emailService.sendEmail(item.recipientEmail(), text.title(), text.body());
                    txService.markSent(item.outboxId());
                } catch (Exception ex) {
                    log.warn("Failed to deliver email outboxId={} to={}: {}",
                            item.outboxId(), item.recipientEmail(), ex.getMessage());
                    txService.recordEmailFailure(item.outboxId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected error processing outboxId={}: {}", item.outboxId(), ex.getMessage(), ex);
        }
    }

    private void pushRealtimeNotification(Long recipientId, NotificationResponse dto) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(recipientId),
                    USER_NOTIFICATIONS_DESTINATION,
                    dto
            );
        } catch (Exception ex) {
            log.warn("WebSocket push failed for notificationId={} userId={}: {}",
                    dto.id(), recipientId, ex.getMessage());
        }
    }

    private NotificationText formatNotificationText(String eventType, Map<String, Object> payload) {
        String className = str(payload.get("className"), "lớp học");
        String studentName = str(payload.get("studentName"), "Sinh viên");
        String title = str(payload.get("title"), str(payload.get("assignmentTitle"), "mục học tập"));

        return switch (eventType) {
            case "CLASS_JOIN_REQUEST_CREATED" -> new NotificationText(
                    "Yêu cầu tham gia lớp học mới",
                    studentName + " vừa gửi yêu cầu tham gia lớp " + className + "."
            );
            case "CLASS_JOIN_REQUEST_APPROVED" -> new NotificationText(
                    "Yêu cầu tham gia lớp đã được duyệt",
                    "Bạn đã được duyệt vào lớp " + className + "."
            );
            case "CLASS_JOIN_REQUEST_REJECTED" -> {
                String reason = str(payload.get("rejectionReason"), "");
                String suffix = StringUtils.hasText(reason) ? " Lý do: " + reason : "";
                yield new NotificationText(
                        "Yêu cầu tham gia lớp bị từ chối",
                        "Yêu cầu tham gia lớp " + className + " đã bị từ chối." + suffix
                );
            }
            case "CLASS_CO_TEACHER_ADDED" -> new NotificationText(
                    "Bạn được phân công đồng giảng dạy",
                    "Bạn đã được thêm làm giảng viên đồng giảng dạy của lớp " + className + "."
            );
            case "ASSIGNMENT_CREATED" -> new NotificationText(
                    "Bài tập mới trong lớp " + className,
                    "Giảng viên vừa giao bài tập \"" + title + "\"."
            );
            case "ASSIGNMENT_SUBMITTED" -> new NotificationText(
                    "Có bài nộp mới",
                    studentName + " vừa nộp bài tập \"" + title + "\"."
            );
            case "ASSIGNMENT_GRADED" -> {
                String score = str(payload.get("score"), "");
                yield new NotificationText(
                        "Bài tập đã được chấm điểm",
                        "Bài tập \"" + title + "\" đã được chấm " + score + " điểm."
                );
            }
            case "ASSIGNMENT_REOPENED" -> new NotificationText(
                    "Bài tập được mở lại quyền nộp",
                    "Giảng viên đã mở lại quyền nộp cho bài tập \"" + title + "\"."
            );
            case "ASSIGNMENT_DUE_SOON" -> new NotificationText(
                    "Nhắc hạn nộp bài tập",
                    "Bài tập \"" + title + "\" trong lớp " + className + " sắp đến hạn nộp trong vòng 24 giờ tới."
            );
            case "CLASS_TEST_CREATED" -> new NotificationText(
                    "Bài kiểm tra mới trong lớp " + className,
                    "Giảng viên vừa tạo bài kiểm tra \"" + title + "\"."
            );
            default -> new NotificationText(
                    "Thông báo mới",
                    "Bạn có một thông báo mới (" + eventType + ")."
            );
        };
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private record NotificationText(String title, String body) {
    }
}
