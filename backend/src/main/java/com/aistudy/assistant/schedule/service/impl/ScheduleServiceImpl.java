package com.aistudy.assistant.schedule.service.impl;

import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.schedule.dto.CalendarEventResponse;
import com.aistudy.assistant.schedule.dto.CalendarView;
import com.aistudy.assistant.schedule.dto.CreatePersonalEventRequest;
import com.aistudy.assistant.schedule.dto.PersonalEventResponse;
import com.aistudy.assistant.schedule.dto.UpdatePersonalEventRequest;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.mapper.ScheduleMapper;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.aistudy.assistant.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Business service for unified calendar aggregation and personal event management (FR-15, FR-16).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final EventRepository eventRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final ScheduleMapper scheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getCalendar(Instant from, Instant to, CalendarView view) {
        Long currentUserId = SecurityUtils.currentUserId();
        CalendarView effectiveView = view != null ? view : CalendarView.WEEK;
        Instant effectiveFrom;
        Instant effectiveTo;

        if (from == null && to == null) {
            effectiveFrom = Instant.now().truncatedTo(ChronoUnit.DAYS);
            effectiveTo = effectiveFrom.plus(effectiveView.defaultDuration());
        } else if (from != null && to == null) {
            effectiveFrom = from;
            effectiveTo = from.plus(effectiveView.defaultDuration());
        } else if (from == null) {
            effectiveTo = to;
            effectiveFrom = to.minus(effectiveView.defaultDuration());
        } else {
            effectiveFrom = from;
            effectiveTo = to;
        }

        EventValidationSupport.validateTimeWindow(effectiveFrom, effectiveTo);

        Map<Long, ClassRole> activeClassRoles = classMemberRepository.findActiveClassRolesByUserId(currentUserId)
                .stream()
                .collect(Collectors.toMap(
                        ActiveClassRoleProjection::getClassId,
                        ActiveClassRoleProjection::getClassRole,
                        (first, ignored) -> first
                ));

        List<Event> events = activeClassRoles.isEmpty()
                ? eventRepository.findOwnerEventsInRange(currentUserId, effectiveFrom, effectiveTo)
                : eventRepository.findCalendarEventsInRange(currentUserId, activeClassRoles.keySet(), effectiveFrom, effectiveTo);

        Instant now = Instant.now();
        return events.stream()
                .map(event -> scheduleMapper.toCalendarEventResponse(
                        event,
                        isReadOnlyForUser(event, currentUserId, activeClassRoles),
                        now
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonalEventResponse> getPersonalEvents(Instant from, Instant to, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (from != null && to != null && !to.isAfter(from)) {
            EventValidationSupport.validateTimeWindow(from, to);
        }

        Instant now = Instant.now();
        return eventRepository.findPersonalEventsByOwner(currentUserId, from, to, pageable)
                .map(event -> scheduleMapper.toPersonalEventResponse(event, now));
    }

    @Override
    @Transactional
    public PersonalEventResponse createPersonalEvent(CreatePersonalEventRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        EventValidationSupport.validateTimeWindow(request.getStartAt(), request.getEndAt());
        String timezone = EventValidationSupport.normalizeAndValidateTimezone(request.getTimezone());
        String meetingUrl = EventValidationSupport.normalizeAndValidateMeetingUrl(request.getMeetingUrl());

        User owner = userRepository.getReferenceById(currentUserId);
        Event event = Event.builder()
                .owner(owner)
                .classroom(null)
                .type(EventType.PERSONAL)
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
        log.info("Created personal event id={} by userId={}", saved.getId(), currentUserId);
        return scheduleMapper.toPersonalEventResponse(saved, Instant.now());
    }

    @Override
    @Transactional
    public PersonalEventResponse updatePersonalEvent(
            Long eventId,
            UpdatePersonalEventRequest request,
            Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Integer expectedVersion = EventValidationSupport.resolveExpectedVersion(ifMatchVersion, request.getVersion());

        Event event = loadEditablePersonalEvent(eventId, currentUserId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new AppException(ErrorCode.UNPROCESSABLE_ENTITY, "Không thể cập nhật sự kiện đã bị hủy.");
        }
        if (!Objects.equals(event.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        EventValidationSupport.validateTimeWindow(request.getStartAt(), request.getEndAt());
        String timezone = EventValidationSupport.normalizeAndValidateTimezone(request.getTimezone());
        String meetingUrl = EventValidationSupport.normalizeAndValidateMeetingUrl(request.getMeetingUrl());

        event.setTitle(request.getTitle().trim());
        event.setDescription(EventValidationSupport.trimToNull(request.getDescription()));
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setTimezone(timezone);
        event.setMeetingUrl(meetingUrl);

        Event updated = eventRepository.saveAndFlush(event);
        log.info("Updated personal event id={} newVersion={} by userId={}",
                updated.getId(), updated.getVersion(), currentUserId);
        return scheduleMapper.toPersonalEventResponse(updated, Instant.now());
    }

    @Override
    @Transactional
    public void deletePersonalEvent(Long eventId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Event event = loadEditablePersonalEvent(eventId, currentUserId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            return;
        }
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
        log.info("Cancelled personal event id={} by userId={}", eventId, currentUserId);
    }

    private Event loadEditablePersonalEvent(Long eventId, Long currentUserId) {
        Event event = eventRepository.findWithDetailsById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getClassroom() != null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không được phép chỉnh sửa hoặc xóa sự kiện thuộc lớp học.");
        }
        if (event.getSource() == EventSource.GOOGLE) {
            throw new AppException(ErrorCode.FORBIDDEN, "Sự kiện đồng bộ từ Google Calendar chỉ ở chế độ xem.");
        }
        if (!event.getOwner().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không phải chủ sở hữu sự kiện này.");
        }
        if (event.getType() != EventType.PERSONAL) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ có thể chỉnh sửa hoặc xóa sự kiện cá nhân.");
        }
        return event;
    }

    private boolean isReadOnlyForUser(Event event, Long currentUserId, Map<Long, ClassRole> activeClassRoles) {
        if (event.getSource() == EventSource.GOOGLE) {
            return true;
        }
        if (event.getClassroom() != null) {
            ClassRole role = activeClassRoles.get(event.getClassroom().getId());
            return role != ClassRole.OWNER_TEACHER && role != ClassRole.CO_TEACHER;
        }
        return !event.getOwner().getId().equals(currentUserId);
    }
}
