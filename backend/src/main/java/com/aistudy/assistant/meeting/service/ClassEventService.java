package com.aistudy.assistant.meeting.service;

import com.aistudy.assistant.meeting.dto.ClassEventResponse;
import com.aistudy.assistant.meeting.dto.CreateClassEventRequest;
import com.aistudy.assistant.meeting.dto.UpdateClassEventRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface ClassEventService {

    ClassEventResponse createClassEvent(Long classId, CreateClassEventRequest request);

    Page<ClassEventResponse> getClassEvents(Long classId, Instant from, Instant to, Pageable pageable);

    ClassEventResponse updateClassEvent(Long eventId, UpdateClassEventRequest request, Integer ifMatchVersion);

    void cancelClassEvent(Long eventId);
}
