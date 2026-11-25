package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.schedule.dto.CalendarEventResponse;
import com.aistudy.assistant.schedule.dto.CalendarView;
import com.aistudy.assistant.schedule.dto.CreatePersonalEventRequest;
import com.aistudy.assistant.schedule.dto.PersonalEventResponse;
import com.aistudy.assistant.schedule.dto.UpdatePersonalEventRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface ScheduleService {

    List<CalendarEventResponse> getCalendar(Instant from, Instant to, CalendarView view);

    Page<PersonalEventResponse> getPersonalEvents(Instant from, Instant to, Pageable pageable);

    PersonalEventResponse createPersonalEvent(CreatePersonalEventRequest request);

    PersonalEventResponse updatePersonalEvent(Long eventId, UpdatePersonalEventRequest request, Integer ifMatchVersion);

    void deletePersonalEvent(Long eventId);
}
