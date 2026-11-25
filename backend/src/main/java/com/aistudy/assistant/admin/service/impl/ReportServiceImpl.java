package com.aistudy.assistant.admin.service.impl;

import com.aistudy.assistant.admin.dto.CreateReportRequest;
import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.entity.Report;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.admin.repository.ReportRepository;
import com.aistudy.assistant.admin.service.ReportService;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ReportResponse createReport(CreateReportRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        User reporter = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Report report = Report.builder()
                .reporter(reporter)
                .type(request.getType().trim().toUpperCase(Locale.ROOT))
                .targetType(request.getTargetType().trim().toUpperCase(Locale.ROOT))
                .targetId(request.getTargetId())
                .detail(request.getDetail().trim())
                .status(ReportStatus.OPEN)
                .build();

        Report saved = reportRepository.save(report);
        log.info("Created report id={} type={} targetType={} targetId={} by userId={}",
                saved.getId(), saved.getType(), saved.getTargetType(), saved.getTargetId(), currentUserId);

        return new ReportResponse(
                saved.getId(),
                reporter.getId(),
                reporter.getFullName(),
                reporter.getEmail(),
                saved.getType(),
                saved.getTargetType(),
                saved.getTargetId(),
                saved.getDetail(),
                saved.getStatus(),
                saved.getResolutionNote(),
                null,
                null,
                saved.getHandledAt(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }
}
