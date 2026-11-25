package com.aistudy.assistant.meeting.mapper;

import com.aistudy.assistant.meeting.dto.ClassEventResponse;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ClassEventMapper {

    public ClassEventResponse toResponse(Event event, Instant now) {
        return new ClassEventResponse(
                event.getId(),
                event.getOwner().getId(),
                event.getClassroom() != null ? event.getClassroom().getId() : null,
                event.getClassroom() != null ? event.getClassroom().getName() : null,
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
