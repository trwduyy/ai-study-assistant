package com.aistudy.assistant.classroom.service.impl;

import com.aistudy.assistant.admin.service.AuditLogService;
import com.aistudy.assistant.classroom.dto.AddCoTeacherRequest;
import com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse;
import com.aistudy.assistant.classroom.dto.ClassMaterialResponse;
import com.aistudy.assistant.classroom.dto.ClassMemberResponse;
import com.aistudy.assistant.classroom.dto.ClassPostResponse;
import com.aistudy.assistant.classroom.dto.ClassroomResponse;
import com.aistudy.assistant.classroom.dto.CreateClassPostRequest;
import com.aistudy.assistant.classroom.dto.CreateClassroomRequest;
import com.aistudy.assistant.classroom.dto.CreateJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.DecideJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.PinClassPostRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomStatusRequest;
import com.aistudy.assistant.classroom.entity.ClassJoinRequest;
import com.aistudy.assistant.classroom.entity.ClassMaterial;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassPost;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.entity.JoinRequestDecision;
import com.aistudy.assistant.classroom.entity.JoinRequestStatus;
import com.aistudy.assistant.classroom.event.ClassMemberActivatedEvent;
import com.aistudy.assistant.classroom.event.ClassMemberDeactivatedEvent;
import com.aistudy.assistant.classroom.event.ClassroomCreatedEvent;
import com.aistudy.assistant.classroom.mapper.ClassroomMapper;
import com.aistudy.assistant.classroom.repository.ClassJoinRequestRepository;
import com.aistudy.assistant.classroom.repository.ClassMaterialRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassPostRepository;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.classroom.service.ClassroomAccessService;
import com.aistudy.assistant.classroom.service.ClassroomService;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.service.FileStorageService;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Business service for Classroom (FR-12): class lifecycle, join-code requests, membership,
 * co-teachers, announcements, and access-controlled shared materials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private static final String CLASS_CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CLASS_CODE_LENGTH = 8;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ClassroomRepository classroomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassJoinRequestRepository classJoinRequestRepository;
    private final ClassPostRepository classPostRepository;
    private final ClassMaterialRepository classMaterialRepository;
    private final UserRepository userRepository;
    private final ClassroomAccessService classroomAccessService;
    private final NotificationOutboxService notificationOutboxService;
    private final FileStorageService fileStorageService;
    private final ClassroomMapper classroomMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        validateDateRange(request.getStartAt(), request.getEndAt());

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String uniqueCode = generateUniqueClassCode();
        Instant now = Instant.now();

        Classroom classroom = Classroom.builder()
                .code(uniqueCode)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .subject(trimToNull(request.getSubject()))
                .avatarUrl(trimToNull(request.getAvatarUrl()))
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(ClassroomStatus.OPEN)
                .owner(owner)
                .build();

        Classroom savedClassroom = classroomRepository.save(classroom);

        ClassMember ownerMembership = ClassMember.builder()
                .classroom(savedClassroom)
                .user(owner)
                .classRole(ClassRole.OWNER_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .joinedAt(now)
                .build();
        classMemberRepository.save(ownerMembership);
        eventPublisher.publishEvent(new ClassroomCreatedEvent(savedClassroom.getId(), owner.getId()));

        log.info("Created classroom id={} code={} by owner userId={}",
                savedClassroom.getId(), savedClassroom.getCode(), currentUserId);
        return classroomMapper.toClassroomResponse(savedClassroom, ClassRole.OWNER_TEACHER, 1L);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassroomResponse> getMyClassrooms(ClassroomStatus status, String keyword, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        String normalizedKeyword = trimToNull(keyword);
        return classroomRepository.findProjectedMyActiveClasses(currentUserId, status, normalizedKeyword, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomResponse getClassroomDetail(Long classId) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassMember membership = classroomAccessService.requireActiveMember(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);
        long memberCount = classMemberRepository.countByClassroomIdAndStatus(classId, ClassMemberStatus.ACTIVE);
        return classroomMapper.toClassroomResponse(classroom, membership.getClassRole(), memberCount);
    }

    @Override
    @Transactional
    public ClassroomResponse updateClassroom(Long classId, UpdateClassroomRequest request, Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thiếu thông tin version để cập nhật lớp học.",
                    List.of(new FieldErrorDetail("version", "Vui lòng cung cấp version trong body hoặc header If-Match"))
            );
        }

        validateDateRange(request.getStartAt(), request.getEndAt());
        ClassMember teacherMember = classroomAccessService.requireTeacherInClass(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);

        if (!Objects.equals(classroom.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        classroom.setName(request.getName().trim());
        classroom.setDescription(trimToNull(request.getDescription()));
        classroom.setSubject(trimToNull(request.getSubject()));
        classroom.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        classroom.setStartAt(request.getStartAt());
        classroom.setEndAt(request.getEndAt());

        Classroom updated = classroomRepository.saveAndFlush(classroom);
        long memberCount = classMemberRepository.countByClassroomIdAndStatus(classId, ClassMemberStatus.ACTIVE);

        log.info("Updated classroom id={}, newVersion={} by userId={}",
                classId, updated.getVersion(), currentUserId);
        return classroomMapper.toClassroomResponse(updated, teacherMember.getClassRole(), memberCount);
    }

    @Override
    @Transactional
    public ClassroomResponse updateClassroomStatus(
            Long classId,
            UpdateClassroomStatusRequest request,
            Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassMember teacherMember = classroomAccessService.requireTeacherInClass(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);

        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion != null && !Objects.equals(classroom.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        ClassroomStatus previousStatus = classroom.getStatus();
        classroom.setStatus(request.getStatus());
        Classroom updated = classroomRepository.saveAndFlush(classroom);
        long memberCount = classMemberRepository.countByClassroomIdAndStatus(classId, ClassMemberStatus.ACTIVE);

        auditLogService.record(
                currentUserId,
                "CLASSROOM_STATUS_UPDATED",
                "CLASSROOM",
                classId,
                Map.of("oldStatus", previousStatus.name(), "newStatus", updated.getStatus().name())
        );
        log.info("Updated classroom status id={} to {} by userId={}",
                classId, updated.getStatus(), currentUserId);
        return classroomMapper.toClassroomResponse(updated, teacherMember.getClassRole(), memberCount);
    }

    @Override
    @Transactional
    public ClassJoinRequestResponse requestJoin(CreateJoinRequestRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        String normalizedCode = request.getCode().trim().toUpperCase(Locale.ROOT);

        Classroom classroom = classroomRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_CODE_INVALID));

        if (classroom.getStatus() != ClassroomStatus.OPEN) {
            throw new AppException(ErrorCode.CLASS_CODE_INVALID);
        }

        Optional<ClassMember> existingMember = classMemberRepository
                .findByClassroomIdAndUserId(classroom.getId(), currentUserId);
        if (existingMember.isPresent() && existingMember.get().getStatus() == ClassMemberStatus.ACTIVE) {
            throw new AppException(ErrorCode.ALREADY_CLASS_MEMBER);
        }

        if (classJoinRequestRepository.existsByClassroomIdAndStudentIdAndStatus(
                classroom.getId(), currentUserId, JoinRequestStatus.PENDING)) {
            throw new AppException(ErrorCode.JOIN_REQUEST_EXISTS);
        }

        User student = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ClassJoinRequest savedRequest;
        try {
            ClassJoinRequest joinRequest = ClassJoinRequest.builder()
                    .classroom(classroom)
                    .student(student)
                    .status(JoinRequestStatus.PENDING)
                    .build();
            savedRequest = classJoinRequestRepository.saveAndFlush(joinRequest);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.JOIN_REQUEST_EXISTS);
        }

        List<ClassMember> teachers = classMemberRepository.findActiveWithUserByClassIdAndRoles(
                classroom.getId(),
                List.of(ClassRole.OWNER_TEACHER, ClassRole.CO_TEACHER)
        );
        Map<String, Object> payload = buildJoinRequestNotificationPayload(classroom, savedRequest, student);
        for (ClassMember teacher : teachers) {
            notificationOutboxService.enqueueForAllChannels(
                    teacher.getUser(),
                    "CLASS_JOIN_REQUEST_CREATED",
                    "CLASS_JOIN_REQUEST",
                    savedRequest.getId(),
                    "",
                    payload
            );
        }

        log.info("Student userId={} requested to join classroom id={}, requestId={}",
                currentUserId, classroom.getId(), savedRequest.getId());
        return classroomMapper.toJoinRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassJoinRequestResponse> getJoinRequests(Long classId, JoinRequestStatus status, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);
        JoinRequestStatus effectiveStatus = status != null ? status : JoinRequestStatus.PENDING;
        return classJoinRequestRepository.findProjectedByClassIdAndStatus(classId, effectiveStatus, pageable);
    }

    @Override
    @Transactional
    public ClassJoinRequestResponse decideJoinRequest(Long classId, Long reqId, DecideJoinRequestRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);

        ClassJoinRequest joinRequest = classJoinRequestRepository.findWithDetailsByIdAndClassroomId(reqId, classId)
                .orElseThrow(() -> new AppException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Yêu cầu tham gia này đã được xử lý trước đó.");
        }

        User decider = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Instant now = Instant.now();

        joinRequest.setDecidedBy(decider);
        joinRequest.setDecidedAt(now);

        Classroom classroom = joinRequest.getClassroom();
        User student = joinRequest.getStudent();
        String eventType;

        if (request.getDecision() == JoinRequestDecision.APPROVED) {
            joinRequest.setStatus(JoinRequestStatus.APPROVED);
            joinRequest.setRejectionReason(null);

            ClassMember member = classMemberRepository
                    .findByClassroomIdAndUserId(classId, student.getId())
                    .orElse(null);

            if (member != null) {
                if (member.getStatus() == ClassMemberStatus.ACTIVE) {
                    throw new AppException(ErrorCode.ALREADY_CLASS_MEMBER);
                }
                member.setStatus(ClassMemberStatus.ACTIVE);
                member.setClassRole(ClassRole.STUDENT);
                member.setJoinedAt(now);
            } else {
                member = ClassMember.builder()
                        .classroom(classroom)
                        .user(student)
                        .classRole(ClassRole.STUDENT)
                        .status(ClassMemberStatus.ACTIVE)
                        .joinedAt(now)
                        .build();
            }
            classMemberRepository.save(member);
            eventPublisher.publishEvent(new ClassMemberActivatedEvent(classId, student.getId()));
            eventType = "CLASS_JOIN_REQUEST_APPROVED";
        } else {
            joinRequest.setStatus(JoinRequestStatus.REJECTED);
            joinRequest.setRejectionReason(trimToNull(request.getRejectionReason()));
            eventType = "CLASS_JOIN_REQUEST_REJECTED";
        }

        ClassJoinRequest saved = classJoinRequestRepository.save(joinRequest);
        auditLogService.record(
                currentUserId,
                "CLASS_JOIN_REQUEST_" + saved.getStatus().name(),
                "CLASS_JOIN_REQUEST",
                saved.getId(),
                Map.of(
                        "classId", classId,
                        "studentId", student.getId(),
                        "decision", saved.getStatus().name()
                )
        );
        Map<String, Object> payload = buildJoinDecisionNotificationPayload(classroom, saved, decider);
        notificationOutboxService.enqueueForAllChannels(
                student,
                eventType,
                "CLASS_JOIN_REQUEST",
                saved.getId(),
                "",
                payload
        );

        log.info("Join request id={} for classId={} decided as {} by teacher userId={}",
                saved.getId(), classId, saved.getStatus(), currentUserId);
        return classroomMapper.toJoinRequestResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassMemberResponse> getMembers(Long classId, ClassRole role, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireActiveMember(classId, currentUserId);
        return classMemberRepository.findProjectedActiveMembers(classId, role, pageable);
    }

    @Override
    @Transactional
    public ClassMemberResponse addCoTeacher(Long classId, AddCoTeacherRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireOwnerTeacher(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);

        User targetUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.UNPROCESSABLE_ENTITY, "Tài khoản giảng viên đang bị khóa.");
        }
        if (targetUser.getRole() != UserRole.TEACHER && targetUser.getRole() != UserRole.ADMIN) {
            throw new AppException(
                    ErrorCode.UNPROCESSABLE_ENTITY,
                    "Chỉ tài khoản có vai trò Giảng viên mới có thể được thêm làm đồng giảng dạy."
            );
        }

        Instant now = Instant.now();
        ClassMember member = classMemberRepository
                .findByClassroomIdAndUserId(classId, targetUser.getId())
                .orElse(null);

        if (member != null) {
            if (member.getStatus() == ClassMemberStatus.ACTIVE
                    && (member.getClassRole() == ClassRole.OWNER_TEACHER
                    || member.getClassRole() == ClassRole.CO_TEACHER)) {
                throw new AppException(ErrorCode.ALREADY_CLASS_MEMBER, "Người dùng này đã là giảng viên của lớp.");
            }
            member.setClassRole(ClassRole.CO_TEACHER);
            member.setStatus(ClassMemberStatus.ACTIVE);
            member.setJoinedAt(now);
        } else {
            member = ClassMember.builder()
                    .classroom(classroom)
                    .user(targetUser)
                    .classRole(ClassRole.CO_TEACHER)
                    .status(ClassMemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();
        }

        ClassMember saved = classMemberRepository.save(member);
        eventPublisher.publishEvent(new ClassMemberActivatedEvent(classId, targetUser.getId()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("classId", classroom.getId());
        payload.put("className", classroom.getName());
        payload.put("classRole", ClassRole.CO_TEACHER.name());
        notificationOutboxService.enqueueForAllChannels(
                targetUser,
                "CLASS_CO_TEACHER_ADDED",
                "CLASSROOM",
                classroom.getId(),
                "co-teacher-" + targetUser.getId(),
                payload
        );

        log.info("Added co-teacher userId={} to classroom id={} by owner userId={}",
                targetUser.getId(), classId, currentUserId);
        return classroomMapper.toClassMemberResponse(saved);
    }

    @Override
    @Transactional
    public void removeMember(Long classId, Long targetUserId) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassMember actor = classroomAccessService.requireTeacherInClass(classId, currentUserId);

        ClassMember target = classMemberRepository
                .findWithClassroomAndUserByClassroomIdAndUserId(classId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy thành viên trong lớp học."));

        if (target.getStatus() != ClassMemberStatus.ACTIVE) {
            throw new AppException(ErrorCode.NOT_FOUND, "Thành viên này hiện không còn hoạt động trong lớp học.");
        }
        if (target.getClassRole() == ClassRole.OWNER_TEACHER) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không thể gỡ giảng viên chủ nhiệm khỏi lớp học.");
        }
        if (target.getClassRole() == ClassRole.CO_TEACHER && actor.getClassRole() != ClassRole.OWNER_TEACHER) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ giảng viên chủ nhiệm mới có quyền gỡ đồng giảng dạy.");
        }

        target.setStatus(ClassMemberStatus.REMOVED);
        classMemberRepository.save(target);
        eventPublisher.publishEvent(new ClassMemberDeactivatedEvent(classId, targetUserId));
        auditLogService.record(
                currentUserId,
                "CLASS_MEMBER_REMOVED",
                "CLASSROOM",
                classId,
                Map.of("targetUserId", targetUserId, "previousRole", target.getClassRole().name())
        );

        log.info("Removed member userId={} from classroom id={} by teacher userId={}",
                targetUserId, classId, currentUserId);
    }

    @Override
    @Transactional
    public void leaveClassroom(Long classId) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassMember member = classroomAccessService.requireActiveMember(classId, currentUserId);

        if (member.getClassRole() == ClassRole.OWNER_TEACHER) {
            throw new AppException(
                    ErrorCode.UNPROCESSABLE_ENTITY,
                    "Giảng viên chủ nhiệm không thể rời lớp học do mình phụ trách."
            );
        }

        member.setStatus(ClassMemberStatus.LEFT);
        classMemberRepository.save(member);
        eventPublisher.publishEvent(new ClassMemberDeactivatedEvent(classId, currentUserId));
        log.info("Member userId={} left classroom id={}", currentUserId, classId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassPostResponse> getPosts(Long classId, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireActiveMember(classId, currentUserId);

        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"))
                );
        return classPostRepository.findProjectedByClassId(classId, effectivePageable);
    }

    @Override
    @Transactional
    public ClassPostResponse createPost(Long classId, CreateClassPostRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);

        User author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ClassPost post = ClassPost.builder()
                .classroom(classroom)
                .author(author)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .pinned(Boolean.TRUE.equals(request.getPinned()))
                .build();

        ClassPost saved = classPostRepository.save(post);
        log.info("Created class post id={} in classroom id={} by userId={}",
                saved.getId(), classId, currentUserId);
        return classroomMapper.toClassPostResponse(saved);
    }

    @Override
    @Transactional
    public ClassPostResponse pinPost(Long classId, Long postId, PinClassPostRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);

        ClassPost post = classPostRepository.findWithAuthorByIdAndClassroomId(postId, classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_POST_NOT_FOUND));

        boolean nextPinned = (request != null && request.getPinned() != null)
                ? request.getPinned()
                : !Boolean.TRUE.equals(post.getPinned());
        post.setPinned(nextPinned);

        ClassPost saved = classPostRepository.save(post);
        log.info("Updated pin state of class post id={} in classroom id={} to {} by userId={}",
                postId, classId, nextPinned, currentUserId);
        return classroomMapper.toClassPostResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassMaterialResponse> getMaterials(Long classId, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireActiveMember(classId, currentUserId);
        return classMaterialRepository.findProjectedByClassId(classId, pageable);
    }

    @Override
    public ClassMaterialResponse uploadMaterial(Long classId, MultipartFile file) {
        Long currentUserId = SecurityUtils.currentUserId();

        // 1. Verify teacher access inside a brief read-only transaction before touching disk
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            classroomAccessService.requireTeacherInClass(classId, currentUserId);
        });

        // 2. Store file outside any database transaction
        FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file, currentUserId);

        try {
            // 3. Persist class material metadata in a short transaction
            ClassMaterial saved = transactionTemplate.execute(status -> {
                classroomAccessService.requireTeacherInClass(classId, currentUserId);
                Classroom classroom = classroomAccessService.requireClassroomExists(classId);
                User uploader = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                ClassMaterial material = ClassMaterial.builder()
                        .classroom(classroom)
                        .uploader(uploader)
                        .fileName(storedFile.sanitizedOriginalName())
                        .storedPath(storedFile.relativeStoredPath())
                        .mimeType(storedFile.mimeType())
                        .sizeBytes(storedFile.sizeBytes())
                        .build();

                return classMaterialRepository.save(material);
            });

            log.info("Uploaded class material id={} for classroom id={} by userId={}",
                    saved.getId(), classId, currentUserId);
            return classroomMapper.toClassMaterialResponse(saved);
        } catch (RuntimeException ex) {
            fileStorageService.deleteFile(storedFile.relativeStoredPath());
            throw ex;
        }
    }

    @Override
    public ClassMaterialDownload downloadMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.currentUserId();

        MaterialFileRef fileRef = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            ClassMaterial material = classMaterialRepository.findWithClassroomById(materialId)
                    .orElseThrow(() -> new AppException(ErrorCode.CLASS_MATERIAL_NOT_FOUND));
            classroomAccessService.requireActiveMember(material.getClassroom().getId(), currentUserId);
            return new MaterialFileRef(material.getFileName(), material.getMimeType(), material.getStoredPath());
        });

        Resource resource = fileStorageService.loadFileAsResource(fileRef.storedPath());
        return new ClassMaterialDownload(fileRef.fileName(), fileRef.mimeType(), resource);
    }

    private String generateUniqueClassCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomClassCode();
            if (!classroomRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể sinh mã lớp học duy nhất. Vui lòng thử lại.");
    }

    private String randomClassCode() {
        StringBuilder sb = new StringBuilder(CLASS_CODE_LENGTH);
        for (int i = 0; i < CLASS_CODE_LENGTH; i++) {
            int idx = SECURE_RANDOM.nextInt(CLASS_CODE_ALPHABET.length());
            sb.append(CLASS_CODE_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private void validateDateRange(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian kết thúc lớp học phải sau thời gian bắt đầu.",
                    List.of(new FieldErrorDetail("endAt", "Thời gian kết thúc không được trước thời gian bắt đầu."))
            );
        }
    }

    private Map<String, Object> buildJoinRequestNotificationPayload(
            Classroom classroom,
            ClassJoinRequest joinRequest,
            User student) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("classId", classroom.getId());
        payload.put("className", classroom.getName());
        payload.put("requestId", joinRequest.getId());
        payload.put("studentId", student.getId());
        payload.put("studentName", student.getFullName());
        payload.put("studentEmail", student.getEmail());
        return payload;
    }

    private Map<String, Object> buildJoinDecisionNotificationPayload(
            Classroom classroom,
            ClassJoinRequest joinRequest,
            User decider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("classId", classroom.getId());
        payload.put("className", classroom.getName());
        payload.put("requestId", joinRequest.getId());
        payload.put("decision", joinRequest.getStatus().name());
        payload.put("decidedById", decider.getId());
        payload.put("decidedByName", decider.getFullName());
        if (joinRequest.getRejectionReason() != null) {
            payload.put("rejectionReason", joinRequest.getRejectionReason());
        }
        return payload;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record MaterialFileRef(String fileName, String mimeType, String storedPath) {
    }
}
