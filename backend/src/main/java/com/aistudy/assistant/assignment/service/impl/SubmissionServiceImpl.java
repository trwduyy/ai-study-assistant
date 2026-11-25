package com.aistudy.assistant.assignment.service.impl;

import com.aistudy.assistant.assignment.dto.GradeSubmissionRequest;
import com.aistudy.assistant.assignment.dto.ReopenSubmissionRequest;
import com.aistudy.assistant.assignment.dto.SubmissionResponse;
import com.aistudy.assistant.assignment.dto.SubmissionSummaryResponse;
import com.aistudy.assistant.assignment.entity.Assignment;
import com.aistudy.assistant.assignment.entity.Submission;
import com.aistudy.assistant.assignment.entity.SubmissionFile;
import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import com.aistudy.assistant.assignment.mapper.AssignmentMapper;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.assignment.repository.SubmissionFileRepository;
import com.aistudy.assistant.assignment.repository.SubmissionRepository;
import com.aistudy.assistant.assignment.service.SubmissionService;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.service.ClassroomAccessService;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.idempotency.IdempotencyService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.service.FileStorageService;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages assignment submissions, server-side UTC deadline/late evaluation, idempotent retries,
 * grading, reopening (D-02), and access-controlled file downloads.
 * Disk I/O executes strictly outside database transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private static final Duration SUBMIT_IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final ClassroomAccessService classroomAccessService;
    private final NotificationOutboxService notificationOutboxService;
    private final IdempotencyService idempotencyService;
    private final FileStorageService fileStorageService;
    private final AssignmentMapper assignmentMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SubmissionResponse submitAssignment(
            Long assignmentId,
            String idempotencyKey,
            String textAnswer,
            List<MultipartFile> files) {
        String normalizedKey = validateAndNormalizeIdempotencyKey(idempotencyKey);
        Long currentUserId = SecurityUtils.currentUserId();
        String trimmedAnswer = trimToNull(textAnswer);
        List<MultipartFile> nonEmptyFiles = filterNonEmptyFiles(files);

        if (trimmedAnswer == null && nonEmptyFiles.isEmpty()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vui lòng nhập nội dung trả lời hoặc đính kèm ít nhất một tệp bài nộp."
            );
        }

        String endpoint = "POST:/api/v1/assignments/" + assignmentId + "/submissions";
        String requestHash = idempotencyService.hashPayload(buildIdempotencyFingerprint(trimmedAnswer, nonEmptyFiles));

        Optional<SubmissionResponse> cached = idempotencyService.findCachedResponse(
                currentUserId, normalizedKey, endpoint, requestHash, SubmissionResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 1. Pre-check membership, window, and duplicate state inside a brief read-only transaction
        SubmissionResponse existingSameKeyResponse = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                    .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
            classroomAccessService.requireActiveMember(assignment.getClassroom().getId(), currentUserId);

            Optional<Submission> existingOpt = submissionRepository
                    .findWithDetailsByAssignmentIdAndStudentId(assignmentId, currentUserId);
            if (existingOpt.isPresent()) {
                Submission existing = existingOpt.get();
                if (normalizedKey.equals(existing.getIdempotencyKey())) {
                    List<SubmissionFile> existingFiles = submissionFileRepository
                            .findBySubmissionIdOrderByIdAsc(existing.getId());
                    return assignmentMapper.toSubmissionResponse(existing, existingFiles);
                }
                if (!existing.isReopenedForResubmission()) {
                    throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_SUBMITTED);
                }
            }

            evaluateSubmissionWindow(assignment, Instant.now());
            return null;
        });

        if (existingSameKeyResponse != null) {
            return existingSameKeyResponse;
        }

        // 2. Store physical files strictly outside any database transaction
        List<FileStorageService.StoredFile> storedFiles = storeFilesOutsideTransaction(nonEmptyFiles, currentUserId);
        List<String> obsoleteOldFilePaths = new ArrayList<>();

        try {
            // 3. Persist submission, files, outbox notifications, and idempotency record in one transaction
            SubmissionResponse response = transactionTemplate.execute(status -> {
                Optional<SubmissionResponse> raceCached = idempotencyService.findCachedResponse(
                        currentUserId, normalizedKey, endpoint, requestHash, SubmissionResponse.class);
                if (raceCached.isPresent()) {
                    return raceCached.get();
                }

                Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                        .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
                classroomAccessService.requireActiveMember(assignment.getClassroom().getId(), currentUserId);

                Instant now = Instant.now();
                boolean isLate = evaluateSubmissionWindow(assignment, now);

                Submission existing = submissionRepository
                        .findByAssignmentIdAndStudentId(assignmentId, currentUserId)
                        .orElse(null);

                Submission submission;
                if (existing != null) {
                    if (normalizedKey.equals(existing.getIdempotencyKey())) {
                        List<SubmissionFile> existingFiles = submissionFileRepository
                                .findBySubmissionIdOrderByIdAsc(existing.getId());
                        return assignmentMapper.toSubmissionResponse(existing, existingFiles);
                    }
                    if (!existing.isReopenedForResubmission()) {
                        throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_SUBMITTED);
                    }
                    List<SubmissionFile> oldFiles = submissionFileRepository
                            .findBySubmissionIdOrderByIdAsc(existing.getId());
                    for (SubmissionFile oldFile : oldFiles) {
                        obsoleteOldFilePaths.add(oldFile.getStoredPath());
                    }
                    submissionFileRepository.deleteBySubmissionId(existing.getId());

                    existing.setTextAnswer(trimmedAnswer);
                    existing.setStatus(SubmissionStatus.SUBMITTED);
                    existing.setIsLate(isLate);
                    existing.setSubmittedAt(now);
                    existing.setScore(null);
                    existing.setFeedback(null);
                    existing.setGradedBy(null);
                    existing.setGradedAt(null);
                    existing.setIdempotencyKey(normalizedKey);
                    submission = existing;
                } else {
                    User student = userRepository.findById(currentUserId)
                            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                    submission = Submission.builder()
                            .assignment(assignment)
                            .student(student)
                            .textAnswer(trimmedAnswer)
                            .status(SubmissionStatus.SUBMITTED)
                            .isLate(isLate)
                            .submittedAt(now)
                            .idempotencyKey(normalizedKey)
                            .build();
                }

                Submission saved;
                try {
                    saved = submissionRepository.saveAndFlush(submission);
                } catch (DataIntegrityViolationException ex) {
                    throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_SUBMITTED);
                }

                List<SubmissionFile> savedFiles = new ArrayList<>();
                for (FileStorageService.StoredFile sf : storedFiles) {
                    savedFiles.add(SubmissionFile.builder()
                            .submission(saved)
                            .fileName(sf.sanitizedOriginalName())
                            .storedPath(sf.relativeStoredPath())
                            .sizeBytes(sf.sizeBytes())
                            .build());
                }
                if (!savedFiles.isEmpty()) {
                    savedFiles = submissionFileRepository.saveAll(savedFiles);
                }

                List<ClassMember> teachers = classMemberRepository.findActiveWithUserByClassIdAndRoles(
                        assignment.getClassroom().getId(),
                        List.of(ClassRole.OWNER_TEACHER, ClassRole.CO_TEACHER)
                );
                Map<String, Object> payload = buildSubmittedNotificationPayload(assignment, saved);
                String reminderKey = "submit-v" + saved.getVersion();
                for (ClassMember teacher : teachers) {
                    notificationOutboxService.enqueueForAllChannels(
                            teacher.getUser(),
                            "ASSIGNMENT_SUBMITTED",
                            "SUBMISSION",
                            saved.getId(),
                            reminderKey,
                            payload
                    );
                }

                SubmissionResponse mapped = assignmentMapper.toSubmissionResponse(saved, savedFiles);
                idempotencyService.saveResponse(
                        currentUserId,
                        normalizedKey,
                        endpoint,
                        requestHash,
                        201,
                        mapped,
                        SUBMIT_IDEMPOTENCY_TTL
                );
                return mapped;
            });

            // Delete replaced files from reopened submission outside transaction
            for (String oldPath : obsoleteOldFilePaths) {
                fileStorageService.deleteFile(oldPath);
            }

            log.info("Submitted assignmentId={} submissionId={} studentId={} late={}",
                    assignmentId, response.getId(), currentUserId, response.getLate());
            return response;
        } catch (RuntimeException ex) {
            cleanupStoredFiles(storedFiles);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubmissionSummaryResponse> getSubmissions(
            Long assignmentId,
            SubmissionStatus status,
            Boolean late,
            Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        classroomAccessService.requireTeacherInClass(assignment.getClassroom().getId(), currentUserId);
        return submissionRepository.findProjectedByAssignmentId(assignmentId, status, late, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionResponse getMySubmission(Long assignmentId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        classroomAccessService.requireActiveMember(assignment.getClassroom().getId(), currentUserId);

        Optional<Submission> submissionOpt = submissionRepository
                .findWithDetailsByAssignmentIdAndStudentId(assignmentId, currentUserId);
        if (submissionOpt.isEmpty()) {
            return null;
        }

        Submission submission = submissionOpt.get();
        List<SubmissionFile> files = submissionFileRepository.findBySubmissionIdOrderByIdAsc(submission.getId());
        return assignmentMapper.toSubmissionResponse(submission, files);
    }

    @Override
    @Transactional
    public SubmissionResponse gradeSubmission(
            Long submissionId,
            GradeSubmissionRequest request,
            Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Submission submission = submissionRepository.findWithDetailsById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBMISSION_NOT_FOUND));
        Assignment assignment = submission.getAssignment();
        classroomAccessService.requireTeacherInClass(assignment.getClassroom().getId(), currentUserId);

        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion != null && !Objects.equals(submission.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        BigDecimal maxScore = assignment.getMaxScore();
        if (request.getScore().compareTo(BigDecimal.ZERO) < 0 || request.getScore().compareTo(maxScore) > 0) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Điểm số phải nằm trong khoảng từ 0 đến " + maxScore + ".",
                    List.of(new FieldErrorDetail("score", "Điểm không được âm và không vượt quá điểm tối đa (" + maxScore + ")."))
            );
        }

        SubmissionStatus targetStatus = request.getStatus() != null ? request.getStatus() : SubmissionStatus.GRADED;
        if (targetStatus != SubmissionStatus.GRADED && targetStatus != SubmissionStatus.RETURNED) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trạng thái chấm bài chỉ chấp nhận GRADED hoặc RETURNED.",
                    List.of(new FieldErrorDetail("status", "Trạng thái không hợp lệ khi chấm điểm."))
            );
        }

        User grader = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Instant now = Instant.now();

        submission.setScore(request.getScore());
        submission.setFeedback(trimToNull(request.getFeedback()));
        submission.setStatus(targetStatus);
        submission.setGradedBy(grader);
        submission.setGradedAt(now);

        Submission saved = submissionRepository.saveAndFlush(submission);
        List<SubmissionFile> files = submissionFileRepository.findBySubmissionIdOrderByIdAsc(saved.getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignment.getId());
        payload.put("assignmentTitle", assignment.getTitle());
        payload.put("submissionId", saved.getId());
        payload.put("score", saved.getScore());
        payload.put("status", saved.getStatus().name());
        notificationOutboxService.enqueueForAllChannels(
                saved.getStudent(),
                "ASSIGNMENT_GRADED",
                "SUBMISSION",
                saved.getId(),
                "grade-v" + saved.getVersion(),
                payload
        );

        log.info("Graded submissionId={} score={} status={} by teacher userId={}",
                submissionId, saved.getScore(), saved.getStatus(), currentUserId);
        return assignmentMapper.toSubmissionResponse(saved, files);
    }

    @Override
    @Transactional
    public SubmissionResponse reopenSubmission(
            Long submissionId,
            ReopenSubmissionRequest request,
            Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Submission submission = submissionRepository.findWithDetailsById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBMISSION_NOT_FOUND));
        Assignment assignment = submission.getAssignment();
        classroomAccessService.requireTeacherInClass(assignment.getClassroom().getId(), currentUserId);

        Integer expectedVersion = ifMatchVersion != null
                ? ifMatchVersion
                : (request != null ? request.getVersion() : null);
        if (expectedVersion != null && !Objects.equals(submission.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        submission.setStatus(SubmissionStatus.RETURNED);
        submission.setScore(null);
        submission.setGradedBy(null);
        submission.setGradedAt(null);
        submission.setIdempotencyKey(null);
        if (request != null && StringUtils.hasText(request.getReason())) {
            submission.setFeedback(request.getReason().trim());
        }

        Submission saved = submissionRepository.saveAndFlush(submission);
        List<SubmissionFile> files = submissionFileRepository.findBySubmissionIdOrderByIdAsc(saved.getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignment.getId());
        payload.put("assignmentTitle", assignment.getTitle());
        payload.put("submissionId", saved.getId());
        if (saved.getFeedback() != null) {
            payload.put("reason", saved.getFeedback());
        }
        notificationOutboxService.enqueueForAllChannels(
                saved.getStudent(),
                "ASSIGNMENT_REOPENED",
                "SUBMISSION",
                saved.getId(),
                "reopen-v" + saved.getVersion(),
                payload
        );

        log.info("Reopened submissionId={} for studentId={} by teacher userId={}",
                submissionId, saved.getStudent().getId(), currentUserId);
        return assignmentMapper.toSubmissionResponse(saved, files);
    }

    @Override
    public SubmissionFileDownload downloadSubmissionFile(Long submissionId, Long fileId) {
        Long currentUserId = SecurityUtils.currentUserId();

        SubmissionFileRef fileRef = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            Submission submission = submissionRepository.findWithDetailsById(submissionId)
                    .orElseThrow(() -> new AppException(ErrorCode.SUBMISSION_NOT_FOUND));
            Long classId = submission.getAssignment().getClassroom().getId();
            ClassMember member = classroomAccessService.requireActiveMember(classId, currentUserId);

            boolean isSubmissionOwner = submission.getStudent().getId().equals(currentUserId);
            boolean isClassTeacher = member.getClassRole() == ClassRole.OWNER_TEACHER
                    || member.getClassRole() == ClassRole.CO_TEACHER;
            if (!isSubmissionOwner && !isClassTeacher) {
                throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền tải tệp bài nộp này.");
            }

            SubmissionFile fileEntity;
            if (fileId != null) {
                fileEntity = submissionFileRepository.findByIdAndSubmissionId(fileId, submissionId)
                        .orElseThrow(() -> new AppException(ErrorCode.SUBMISSION_FILE_NOT_FOUND));
            } else {
                List<SubmissionFile> files = submissionFileRepository.findBySubmissionIdOrderByIdAsc(submissionId);
                if (files.isEmpty()) {
                    throw new AppException(ErrorCode.SUBMISSION_FILE_NOT_FOUND);
                }
                fileEntity = files.get(0);
            }

            return new SubmissionFileRef(
                    fileEntity.getFileName(),
                    inferMimeType(fileEntity.getFileName()),
                    fileEntity.getStoredPath()
            );
        });

        Resource resource = fileStorageService.loadFileAsResource(fileRef.storedPath());
        return new SubmissionFileDownload(fileRef.fileName(), fileRef.mimeType(), resource);
    }

    private boolean evaluateSubmissionWindow(Assignment assignment, Instant now) {
        if (assignment.getOpenAt() != null && now.isBefore(assignment.getOpenAt())) {
            throw new AppException(ErrorCode.UNPROCESSABLE_ENTITY, "Bài tập chưa đến thời gian mở nộp.");
        }
        boolean isLate = assignment.getDueAt() != null && now.isAfter(assignment.getDueAt());
        if (isLate && !Boolean.TRUE.equals(assignment.getAllowLate())) {
            throw new AppException(ErrorCode.ASSIGNMENT_CLOSED);
        }
        return isLate;
    }

    private String validateAndNormalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header Idempotency-Key là bắt buộc khi nộp bài.");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không được vượt quá 64 ký tự.");
        }
        return trimmed;
    }

    private List<MultipartFile> filterNonEmptyFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();
    }

    private List<FileStorageService.StoredFile> storeFilesOutsideTransaction(
            List<MultipartFile> files,
            Long userId) {
        if (files.isEmpty()) {
            return List.of();
        }
        List<FileStorageService.StoredFile> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                stored.add(fileStorageService.storeFile(file, userId));
            }
            return stored;
        } catch (RuntimeException ex) {
            cleanupStoredFiles(stored);
            throw ex;
        }
    }

    private void cleanupStoredFiles(List<FileStorageService.StoredFile> storedFiles) {
        for (FileStorageService.StoredFile sf : storedFiles) {
            fileStorageService.deleteFile(sf.relativeStoredPath());
        }
    }

    private Map<String, Object> buildIdempotencyFingerprint(String textAnswer, List<MultipartFile> files) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("textAnswer", textAnswer != null ? textAnswer : "");
        List<String> fileSpecs = files.stream()
                .map(f -> (f.getOriginalFilename() != null ? f.getOriginalFilename() : "") + ":" + f.getSize())
                .toList();
        fingerprint.put("files", fileSpecs);
        return fingerprint;
    }

    private Map<String, Object> buildSubmittedNotificationPayload(Assignment assignment, Submission submission) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignment.getId());
        payload.put("assignmentTitle", assignment.getTitle());
        payload.put("submissionId", submission.getId());
        payload.put("studentId", submission.getStudent().getId());
        payload.put("studentName", submission.getStudent().getFullName());
        payload.put("late", submission.getIsLate());
        payload.put("submittedAt", submission.getSubmittedAt().toString());
        return payload;
    }

    private String inferMimeType(String fileName) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record SubmissionFileRef(String fileName, String mimeType, String storedPath) {
    }
}
