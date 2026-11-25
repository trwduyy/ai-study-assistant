package com.aistudy.assistant.schedule.mapper;

import com.aistudy.assistant.schedule.dto.CalendarEventResponse;
import com.aistudy.assistant.schedule.dto.PersonalEventResponse;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ScheduleMapper {

    public CalendarEventResponse toCalendarEventResponse(Event event, boolean readOnly, Instant now) {
        return new CalendarEventResponse(
                event.getId(),
                event.getOwner().getId(),
                event.getClassroom() != null ? event.getClassroom().getId() : null,
                event.getClassroom() != null ? event.getClassroom().getName() : null,
                event.getCalendarImport() != null ? event.getCalendarImport().getId() : null,
                event.getType(),
                event.getTitle(),
                event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.getTimezone(),
                EventValidationSupport.computeEffectiveStatus(event, now),
                event.getMeetingUrl(),
                event.getSource(),
                event.getExternalId(),
                readOnly,
                event.getVersion(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    public PersonalEventResponse toPersonalEventResponse(Event event, Instant now) {
        return new PersonalEventResponse(
                event.getId(),
                event.getOwner().getId(),
                event.getType(),
                event.getTitle(),
                event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.getTimezone(),
                EventValidationSupport.computeEffectiveStatus(event, now),
                event.getMeetingUrl(),
                event.getSource(),
                event.getVersion(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
