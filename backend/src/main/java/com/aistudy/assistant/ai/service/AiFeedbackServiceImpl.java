package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.admin.entity.Report;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.admin.repository.ReportRepository;
import com.aistudy.assistant.ai.dto.AiFeedbackResponse;
import com.aistudy.assistant.ai.dto.CreateAiFeedbackRequest;
import com.aistudy.assistant.ai.entity.AiFeedback;
import com.aistudy.assistant.ai.entity.FeedbackStatus;
import com.aistudy.assistant.ai.mapper.AiMapper;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiFeedbackRepository;
import com.aistudy.assistant.ai.repository.AiSummaryRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final AiFeedbackRepository aiFeedbackRepository;
    private final ReportRepository reportRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiSummaryRepository aiSummaryRepository;
    private final QuizRepository quizRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final AiMapper aiMapper;

    @Override
    @Transactional
    public AiFeedbackResponse submitFeedback(CreateAiFeedbackRequest request) {
        Long userId = SecurityUtils.currentUserId();
        validateTargetOwnership(request, userId);

        User user = userRepository.getReferenceById(userId);
        String reason = request.reason().trim();
        AiFeedback feedback = AiFeedback.builder()
                .user(user)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reason(reason)
                .status(FeedbackStatus.OPEN)
                .build();
        feedback = aiFeedbackRepository.save(feedback);

        reportRepository.save(Report.builder()
                .reporter(user)
                .type("AI_INACCURATE")
                .targetType(request.targetType().name())
                .targetId(request.targetId())
                .detail(reason)
                .status(ReportStatus.OPEN)
                .build());

        return aiMapper.toFeedbackResponse(feedback);
    }

    private void validateTargetOwnership(CreateAiFeedbackRequest request, Long userId) {
        Long targetId = request.targetId();
        switch (request.targetType()) {
            case AI_CHAT_MESSAGE -> {
                var msg = aiChatMessageRepository.findWithChatById(targetId)
                        .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn AI cần báo cáo."));
                if (!msg.getChat().getUser().getId().equals(userId)) {
                    throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
                }
            }
            case AI_SUMMARY -> {
                var summary = aiSummaryRepository.findWithDetailsById(targetId)
                        .orElseThrow(() -> new AppException(ErrorCode.AI_SUMMARY_NOT_FOUND));
                if (!summary.getUser().getId().equals(userId)) {
                    throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
                }
            }
            case QUIZ -> {
                var quiz = quizRepository.findByIdAndDeletedAtIsNull(targetId)
                        .orElseThrow(() -> new AppException(ErrorCode.QUIZ_NOT_FOUND));
                if (!quiz.getOwner().getId().equals(userId)) {
                    throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
                }
            }
            case AI_EXPLANATION -> {
                var material = materialRepository.findByIdAndDeletedAtIsNull(targetId)
                        .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));
                if (!material.getOwner().getId().equals(userId)) {
                    throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
                }
            }
        }
    }
}
