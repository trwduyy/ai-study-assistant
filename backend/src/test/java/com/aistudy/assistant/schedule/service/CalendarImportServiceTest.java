package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.schedule.dto.CalendarImportPreviewResponse;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportRequest;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportResponse;
import com.aistudy.assistant.schedule.entity.CalendarImport;
import com.aistudy.assistant.schedule.entity.CalendarImportSourceType;
import com.aistudy.assistant.schedule.entity.CalendarImportStatus;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.repository.CalendarImportRepository;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.service.impl.CalendarImportServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarImportServiceTest {

    @Mock
    private CalendarImportRepository calendarImportRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    private CalendarImportServiceImpl calendarImportService;
    private User studentUser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        calendarImportService = new CalendarImportServiceImpl(
                calendarImportRepository,
                eventRepository,
                classMemberRepository,
                userRepository,
                new CalendarImportParser(),
                objectMapper,
                transactionTemplate
        );

        studentUser = User.builder()
                .id(11L)
                .email("sv01@actvn.edu.vn")
                .fullName("Nguyễn Văn A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        UserPrincipal principal = UserPrincipal.create(studentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("previewImport detects overlapping calendar events and marks hasConflict=true")
    void previewImport_OverlappingEvent_MarksConflict() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        String csv = """
                title,description,start_at,end_at,timezone,location
                "Lịch trùng lớp","Ôn tập",2026-11-26T01:30:00Z,2026-11-26T02:30:00Z,Asia/Ho_Chi_Minh,"P1"
                "Lịch không trùng","Tự học",2026-11-26T04:00:00Z,2026-11-26T05:00:00Z,Asia/Ho_Chi_Minh,"P2"
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "schedule.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        ActiveClassRoleProjection roleProj = mock(ActiveClassRoleProjection.class);
        when(roleProj.getClassId()).thenReturn(10L);
        when(classMemberRepository.findActiveClassRolesByUserId(11L)).thenReturn(List.of(roleProj));

        Classroom classroom = Classroom.builder().id(10L).name("Lập trình Mạng").build();
        Event existingClassEvent = Event.builder()
                .id(501L)
                .classroom(classroom)
                .type(EventType.CLASS)
                .title("Buổi học TCP")
                .startAt(Instant.parse("2026-11-26T01:00:00Z"))
                .endAt(Instant.parse("2026-11-26T02:00:00Z"))
                .source(EventSource.MANUAL)
                .status(EventStatus.UPCOMING)
                .build();
        when(eventRepository.findOverlappingCalendarEvents(
                eq(11L),
                eq(List.of(10L)),
                eq(Instant.parse("2026-11-26T01:30:00Z")),
                eq(Instant.parse("2026-11-26T05:00:00Z")),
                isNull()))
                .thenReturn(List.of(existingClassEvent));

        when(userRepository.getReferenceById(11L)).thenReturn(studentUser);
        when(calendarImportRepository.save(any(CalendarImport.class))).thenAnswer(inv -> {
            CalendarImport imp = inv.getArgument(0);
            imp.setId(45L);
            return imp;
        });

        CalendarImportPreviewResponse preview = calendarImportService.previewImport(file, "Asia/Ho_Chi_Minh");

        assertEquals(45L, preview.importId());
        assertEquals(CalendarImportStatus.PREVIEW, preview.status());
        assertEquals(2, preview.totalParsed());
        assertEquals(1, preview.conflictCount());
        assertTrue(preview.items().get(0).hasConflict());
        assertEquals(501L, preview.items().get(0).conflictingEvents().get(0).eventId());
        assertFalse(preview.items().get(1).hasConflict());
    }

    @Test
    @DisplayName("confirmImport saves only accepted indices as PERSONAL events and confirms batch")
    void confirmImport_SelectedIndices_SavesEventsAndConfirmsBatch() {
        String previewJson = """
                [
                    {
                        "itemIndex": 0,
                        "externalId": null,
                        "title": "Sự kiện 0",
                        "description": "Mô tả 0",
                        "location": "P101",
                        "startAt": "2026-11-26T01:30:00Z",
                        "endAt": "2026-11-26T02:30:00Z",
                        "timezone": "Asia/Ho_Chi_Minh"
                    },
                    {
                        "itemIndex": 1,
                        "externalId": null,
                        "title": "Sự kiện 1",
                        "description": "Mô tả 1",
                        "location": null,
                        "startAt": "2026-11-26T04:00:00Z",
                        "endAt": "2026-11-26T05:00:00Z",
                        "timezone": "Asia/Ho_Chi_Minh"
                    }
                ]
                """;
        CalendarImport existingImport = CalendarImport.builder()
                .id(45L)
                .user(studentUser)
                .sourceType(CalendarImportSourceType.CSV)
                .fileName("schedule.csv")
                .timezone("Asia/Ho_Chi_Minh")
                .status(CalendarImportStatus.PREVIEW)
                .previewJson(previewJson)
                .build();

        when(calendarImportRepository.findById(45L)).thenReturn(Optional.of(existingImport));
        when(userRepository.getReferenceById(11L)).thenReturn(studentUser);
        AtomicLong seq = new AtomicLong(3000L);
        when(eventRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Event> events = inv.getArgument(0);
            for (Event e : events) {
                e.setId(seq.incrementAndGet());
            }
            return events;
        });

        ConfirmCalendarImportResponse response = calendarImportService.confirmImport(
                45L,
                ConfirmCalendarImportRequest.builder().acceptedItemIndices(List.of(1)).build()
        );

        assertEquals(CalendarImportStatus.CONFIRMED, response.status());
        assertEquals(1, response.importedCount());
        assertEquals(1, response.skippedCount());
        assertEquals(List.of(3001L), response.createdEventIds());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Event>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(savedCaptor.capture());
        assertEquals(1, savedCaptor.getValue().size());
        assertEquals(EventType.PERSONAL, savedCaptor.getValue().get(0).getType());
        assertEquals(EventSource.CSV, savedCaptor.getValue().get(0).getSource());
        assertEquals("Sự kiện 1", savedCaptor.getValue().get(0).getTitle());
    }
}
