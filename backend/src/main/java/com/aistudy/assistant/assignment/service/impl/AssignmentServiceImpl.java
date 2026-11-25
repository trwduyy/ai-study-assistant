package com.aistudy.assistant.assignment.service.impl;

import com.aistudy.assistant.assignment.dto.AssignmentResponse;
import com.aistudy.assistant.assignment.dto.AssignmentSummaryResponse;
import com.aistudy.assistant.assignment.dto.CreateAssignmentRequest;
import com.aistudy.assistant.assignment.dto.UpdateAssignmentRequest;
import com.aistudy.assistant.assignment.entity.Assignment;
import com.aistudy.assistant.assignment.entity.AssignmentAttachment;
import com.aistudy.assistant.assignment.entity.Submission;
import com.aistudy.assistant.assignment.mapper.AssignmentMapper;
import com.aistudy.assistant.assignment.repository.AssignmentAttachmentRepository;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.assignment.repository.SubmissionFileRepository;
import com.aistudy.assistant.assignment.repository.SubmissionRepository;
import com.aistudy.assistant.assignment.service.AssignmentService;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.service.ClassroomAccessService;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.service.FileStorageService;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Business service for classroom assignments (FR-13): creation with optional attachments,
 * student listing with submission status, optimistic-locked updates, and cleanup on deletion.
 * Disk I/O executes strictly outside database transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private static final BigDecimal DEFAULT_MAX_SCORE = new BigDecimal("10.00");

    private final AssignmentRepository assignmentRepository;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final ClassroomAccessService classroomAccessService;
    private final NotificationOutboxService notificationOutboxService;
    private final FileStorageService fileStorageService;
    private final AssignmentMapper assignmentMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AssignmentResponse createAssignment(
            Long classId,
            CreateAssignmentRequest request,
            List<MultipartFile> files) {
        Long currentUserId = SecurityUtils.currentUserId();
        validateDateWindow(request.getOpenAt(), request.getDueAt());

        // 1. Validate teacher access in a brief read-only transaction before disk I/O
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            classroomAccessService.requireTeacherInClass(classId, currentUserId);
        });

        // 2. Store optional attachment files outside any database transaction
        List<FileStorageService.StoredFile> storedFiles = storeFilesOutsideTransaction(files, currentUserId);

        try {
            // 3. Persist assignment, attachment metadata, and outbox notifications atomically
            AssignmentResult result = transactionTemplate.execute(status -> {
                classroomAccessService.requireTeacherInClass(classId, currentUserId);
                Classroom classroom = classroomAccessService.requireClassroomExists(classId);
                User creator = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                Assignment assignment = Assignment.builder()
                        .classroom(classroom)
                        .creator(creator)
                        .title(request.getTitle().trim())
                        .instructions(trimToNull(request.getInstructions()))
                        .maxScore(request.getMaxScore() != null ? request.getMaxScore() : DEFAULT_MAX_SCORE)
                        .openAt(request.getOpenAt())
                        .dueAt(request.getDueAt())
                        .allowLate(Boolean.TRUE.equals(request.getAllowLate()))
                        .maxSubmissions(request.getMaxSubmissions() != null ? request.getMaxSubmissions() : 1)
                        .build();

                Assignment saved = assignmentRepository.save(assignment);

                List<AssignmentAttachment> attachments = new ArrayList<>();
                for (FileStorageService.StoredFile sf : storedFiles) {
                    attachments.add(AssignmentAttachment.builder()
                            .assignment(saved)
                            .fileName(sf.sanitizedOriginalName())
                            .storedPath(sf.relativeStoredPath())
                            .build());
                }
                if (!attachments.isEmpty()) {
                    attachments = assignmentAttachmentRepository.saveAll(attachments);
                }

                List<ClassMember> activeStudents = classMemberRepository.findActiveWithUserByClassIdAndRoles(
                        classId,
                        List.of(ClassRole.STUDENT)
                );
                Map<String, Object> payload = buildAssignmentCreatedPayload(classroom, saved);
                for (ClassMember studentMember : activeStudents) {
                    notificationOutboxService.enqueueForAllChannels(
                            studentMember.getUser(),
                            "ASSIGNMENT_CREATED",
                            "ASSIGNMENT",
                            saved.getId(),
                            "",
                            payload
                    );
                }

                return new AssignmentResult(saved, attachments);
            });

            log.info("Created assignment id={} in classId={} by teacher userId={}",
                    result.assignment().getId(), classId, currentUserId);
            return assignmentMapper.toAssignmentResponse(result.assignment(), result.attachments(), null, null);
        } catch (RuntimeException ex) {
            cleanupStoredFiles(storedFiles);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentSummaryResponse> getAssignments(Long classId, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireActiveMember(classId, currentUserId);
        return assignmentRepository.findProjectedByClassIdAndStudent(classId, currentUserId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentDetail(Long assignmentId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        classroomAccessService.requireActiveMember(assignment.getClassroom().getId(), currentUserId);

        List<AssignmentAttachment> attachments = assignmentAttachmentRepository
                .findByAssignmentIdOrderByIdAsc(assignmentId);
        Submission mySubmission = submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, currentUserId)
                .orElse(null);

        return assignmentMapper.toAssignmentResponse(
                assignment,
                attachments,
                mySubmission != null ? mySubmission.getId() : null,
                mySubmission != null ? mySubmission.getStatus() : null
        );
    }

    @Override
    @Transactional
    public AssignmentResponse updateAssignment(
            Long assignmentId,
            UpdateAssignmentRequest request,
            Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thiếu thông tin version để cập nhật bài tập.",
                    List.of(new FieldErrorDetail("version", "Vui lòng cung cấp version trong body hoặc header If-Match"))
            );
        }

        validateDateWindow(request.getOpenAt(), request.getDueAt());

        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        classroomAccessService.requireTeacherInClass(assignment.getClassroom().getId(), currentUserId);

        if (!Objects.equals(assignment.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        assignment.setTitle(request.getTitle().trim());
        assignment.setInstructions(trimToNull(request.getInstructions()));
        if (request.getMaxScore() != null) {
            assignment.setMaxScore(request.getMaxScore());
        }
        assignment.setOpenAt(request.getOpenAt());
        assignment.setDueAt(request.getDueAt());
        if (request.getAllowLate() != null) {
            assignment.setAllowLate(request.getAllowLate());
        }
        if (request.getMaxSubmissions() != null) {
            assignment.setMaxSubmissions(request.getMaxSubmissions());
        }

        Assignment updated = assignmentRepository.saveAndFlush(assignment);
        List<AssignmentAttachment> attachments = assignmentAttachmentRepository
                .findByAssignmentIdOrderByIdAsc(assignmentId);

        log.info("Updated assignment id={}, newVersion={} by teacher userId={}",
                assignmentId, updated.getVersion(), currentUserId);
        return assignmentMapper.toAssignmentResponse(updated, attachments, null, null);
    }

    @Override
    public void deleteAssignment(Long assignmentId) {
        Long currentUserId = SecurityUtils.currentUserId();

        List<String> pathsToDelete = transactionTemplate.execute(status -> {
            Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                    .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
            classroomAccessService.requireTeacherInClass(assignment.getClassroom().getId(), currentUserId);

            List<String> paths = new ArrayList<>();
            paths.addAll(assignmentAttachmentRepository.findStoredPathsByAssignmentId(assignmentId));
            paths.addAll(submissionFileRepository.findStoredPathsByAssignmentId(assignmentId));

            assignmentRepository.delete(assignment);
            return paths;
        });

        if (pathsToDelete != null) {
            for (String path : pathsToDelete) {
                fileStorageService.deleteFile(path);
            }
        }
        log.info("Deleted assignment id={} by teacher userId={}", assignmentId, currentUserId);
    }

    private List<FileStorageService.StoredFile> storeFilesOutsideTransaction(
            List<MultipartFile> files,
            Long userId) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<FileStorageService.StoredFile> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    stored.add(fileStorageService.storeFile(file, userId));
                }
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

    private void validateDateWindow(Instant openAt, Instant dueAt) {
        if (openAt != null && dueAt != null && dueAt.isBefore(openAt)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Hạn nộp bài tập phải sau thời gian mở nộp.",
                    List.of(new FieldErrorDetail("dueAt", "Hạn nộp không được trước thời gian mở nộp."))
            );
        }
    }

    private Map<String, Object> buildAssignmentCreatedPayload(Classroom classroom, Assignment assignment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("classId", classroom.getId());
        payload.put("className", classroom.getName());
        payload.put("assignmentId", assignment.getId());
        payload.put("title", assignment.getTitle());
        if (assignment.getDueAt() != null) {
            payload.put("dueAt", assignment.getDueAt().toString());
        }
        return payload;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record AssignmentResult(Assignment assignment, List<AssignmentAttachment> attachments) {
    }
}
