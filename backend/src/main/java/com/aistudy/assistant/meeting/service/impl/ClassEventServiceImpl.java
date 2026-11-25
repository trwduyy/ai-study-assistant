package com.aistudy.assistant.meeting.service.impl;

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
import com.aistudy.assistant.meeting.dto.ClassEventResponse;
import com.aistudy.assistant.meeting.dto.CreateClassEventRequest;
import com.aistudy.assistant.meeting.dto.UpdateClassEventRequest;
import com.aistudy.assistant.meeting.mapper.ClassEventMapper;
import com.aistudy.assistant.meeting.service.ClassEventService;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Business service for classroom learning/test/meeting events (FR-15) with outbox notifications
 * to active class students and optimistic-locking enforcement on updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassEventServiceImpl implements ClassEventService {

    private static final Set<EventType> ALLOWED_CLASS_EVENT_TYPES = EnumSet.of(
            EventType.CLASS,
            EventType.TEST,
            EventType.MEETING
    );

    private final EventRepository eventRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final ClassroomAccessService classroomAccessService;
    private final NotificationOutboxService notificationOutboxService;
    private final ClassEventMapper classEventMapper;

    @Override
    @Transactional
    public ClassEventResponse createClassEvent(Long classId, CreateClassEventRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);

        validateClassEventType(request.getType());
        EventValidationSupport.validateTimeWindow(request.getStartAt(), request.getEndAt());
        String timezone = EventValidationSupport.normalizeAndValidateTimezone(request.getTimezone());
        String meetingUrl = EventValidationSupport.normalizeAndValidateMeetingUrl(request.getMeetingUrl());

        User owner = userRepository.getReferenceById(currentUserId);
        Event event = Event.builder()
                .owner(owner)
                .classroom(classroom)
                .type(request.getType())
                .title(request.getTitle().trim())
                .description(EventValidationSupport.trimToNull(request.getDescription()))
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .timezone(timezone)
                .status(EventStatus.UPCOMING)
                .meetingUrl(meetingUrl)
                .source(EventSource.MANUAL)
                .build();

        Event saved = eventRepository.save(event);
        notifyClassStudents(classroom, saved, "CLASS_EVENT_CREATED", "");

        log.info("Created class event id={} type={} classId={} by teacher userId={}",
                saved.getId(), saved.getType(), classId, currentUserId);
        return classEventMapper.toResponse(saved, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassEventResponse> getClassEvents(Long classId, Instant from, Instant to, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireActiveMember(classId, currentUserId);

        if (from != null && to != null && !to.isAfter(from)) {
            EventValidationSupport.validateTimeWindow(from, to);
        }

        Instant now = Instant.now();
        return eventRepository.findClassEventsByClassId(classId, from, to, pageable)
                .map(event -> classEventMapper.toResponse(event, now));
    }

    @Override
    @Transactional
    public ClassEventResponse updateClassEvent(Long eventId, UpdateClassEventRequest request, Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Integer expectedVersion = EventValidationSupport.resolveExpectedVersion(ifMatchVersion, request.getVersion());

        Event event = eventRepository.findWithDetailsById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getClassroom() == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ sự kiện thuộc lớp học mới được chỉnh sửa qua endpoint này.");
        }

        Classroom classroom = event.getClassroom();
        classroomAccessService.requireTeacherInClass(classroom.getId(), currentUserId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new AppException(ErrorCode.UNPROCESSABLE_ENTITY, "Không thể cập nhật sự kiện đã bị hủy.");
        }
        if (!Objects.equals(event.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        validateClassEventType(request.getType());
        EventValidationSupport.validateTimeWindow(request.getStartAt(), request.getEndAt());
        String timezone = EventValidationSupport.normalizeAndValidateTimezone(request.getTimezone());
        String meetingUrl = EventValidationSupport.normalizeAndValidateMeetingUrl(request.getMeetingUrl());

        event.setType(request.getType());
        event.setTitle(request.getTitle().trim());
        event.setDescription(EventValidationSupport.trimToNull(request.getDescription()));
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setTimezone(timezone);
        event.setMeetingUrl(meetingUrl);

        Event updated = eventRepository.saveAndFlush(event);
        String reminderKey = "V" + updated.getVersion();
        notifyClassStudents(classroom, updated, "CLASS_EVENT_UPDATED", reminderKey);

        log.info("Updated class event id={} newVersion={} classId={} by teacher userId={}",
                updated.getId(), updated.getVersion(), classroom.getId(), currentUserId);
        return classEventMapper.toResponse(updated, Instant.now());
    }

    @Override
    @Transactional
    public void cancelClassEvent(Long eventId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Event event = eventRepository.findWithDetailsById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getClassroom() == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ sự kiện thuộc lớp học mới được hủy qua endpoint này.");
        }

        Classroom classroom = event.getClassroom();
        classroomAccessService.requireTeacherInClass(classroom.getId(), currentUserId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            return;
        }

        event.setStatus(EventStatus.CANCELLED);
        Event cancelled = eventRepository.save(event);
        notifyClassStudents(classroom, cancelled, "CLASS_EVENT_CANCELLED", "");

        log.info("Cancelled class event id={} classId={} by teacher userId={}",
                cancelled.getId(), classroom.getId(), currentUserId);
    }

    private void validateClassEventType(EventType type) {
        if (type == null || !ALLOWED_CLASS_EVENT_TYPES.contains(type)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Sự kiện lớp học chỉ hỗ trợ loại CLASS, TEST hoặc MEETING.",
                    List.of(new FieldErrorDetail("type", "Loại sự kiện hợp lệ: CLASS, TEST, MEETING."))
            );
        }
    }

    private void notifyClassStudents(Classroom classroom, Event event, String eventType, String reminderKey) {
        List<ClassMember> activeStudents = classMemberRepository.findActiveWithUserByClassIdAndRoles(
                classroom.getId(),
                List.of(ClassRole.STUDENT)
        );
        if (activeStudents.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId());
        payload.put("classId", classroom.getId());
        payload.put("className", classroom.getName());
        payload.put("type", event.getType().name());
        payload.put("title", event.getTitle());
        payload.put("startAt", event.getStartAt().toString());
        payload.put("endAt", event.getEndAt().toString());
        payload.put("timezone", event.getTimezone());
        if (event.getMeetingUrl() != null) {
            payload.put("meetingUrl", event.getMeetingUrl());
        }

        for (ClassMember student : activeStudents) {
            notificationOutboxService.enqueueForAllChannels(
                    student.getUser(),
                    eventType,
                    "EVENT",
                    event.getId(),
                    reminderKey,
                    payload
            );
        }
    }
}
