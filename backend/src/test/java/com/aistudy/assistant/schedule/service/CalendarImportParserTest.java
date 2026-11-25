package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.schedule.dto.StoredImportPreviewItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarImportParserTest {

    private final CalendarImportParser parser = new CalendarImportParser();

    @Test
    @DisplayName("parseIcs parses VEVENTs with TZID and UTC into normalized UTC Instants")
    void parseIcs_ValidCalendar_NormalizesUtcAndPreservesTimezone() {
        String ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//AI Study Assistant//Test//EN
                BEGIN:VEVENT
                UID:evt-101@actvn.edu.vn
                SUMMARY:Lập trình Mạng
                DESCRIPTION:Chương 3 TCP
                LOCATION:Phòng 402-TA2
                DTSTART;TZID=Asia/Ho_Chi_Minh:20261126T080000
                DTEND;TZID=Asia/Ho_Chi_Minh:20261126T100000
                END:VEVENT
                BEGIN:VEVENT
                UID:evt-102@actvn.edu.vn
                SUMMARY:Họp trực tuyến
                DTSTART:20261127T010000Z
                DTEND:20261127T023000Z
                END:VEVENT
                END:VCALENDAR
                """;

        List<StoredImportPreviewItem> items = parser.parseIcs(
                ics.getBytes(StandardCharsets.UTF_8),
                "Asia/Ho_Chi_Minh"
        );

        assertEquals(2, items.size());
        assertEquals("Lập trình Mạng", items.get(0).title());
        assertEquals("Asia/Ho_Chi_Minh", items.get(0).timezone());
        assertEquals(Instant.parse("2026-11-26T01:00:00Z"), items.get(0).startAt());
        assertEquals(Instant.parse("2026-11-26T03:00:00Z"), items.get(0).endAt());
        assertEquals("Phòng 402-TA2", items.get(0).location());
        assertEquals("evt-101@actvn.edu.vn", items.get(0).externalId());

        assertEquals("UTC", items.get(1).timezone());
        assertEquals(Instant.parse("2026-11-27T01:00:00Z"), items.get(1).startAt());
        assertEquals(Instant.parse("2026-11-27T02:30:00Z"), items.get(1).endAt());
    }

    @Test
    @DisplayName("parseCsv parses valid UTF-8 CSV rows into UTC Instants")
    void parseCsv_ValidUtf8Csv_ParsesEvents() {
        String csv = """
                title,description,start_at,end_at,timezone,location
                "Ôn tập Hệ điều hành","Đọc chương 4",2026-11-28 08:00:00,2026-11-28 10:00:00,Asia/Ho_Chi_Minh,"Thư viện"
                """;

        List<StoredImportPreviewItem> items = parser.parseCsv(
                csv.getBytes(StandardCharsets.UTF_8),
                "Asia/Ho_Chi_Minh"
        );

        assertEquals(1, items.size());
        assertEquals("Ôn tập Hệ điều hành", items.get(0).title());
        assertEquals(Instant.parse("2026-11-28T01:00:00Z"), items.get(0).startAt());
        assertEquals(Instant.parse("2026-11-28T03:00:00Z"), items.get(0).endAt());
        assertEquals("Asia/Ho_Chi_Minh", items.get(0).timezone());
        assertEquals("Thư viện", items.get(0).location());
    }

    @Test
    @DisplayName("parseCsv throws 400 VALIDATION_ERROR when required header column is missing")
    void parseCsv_MissingRequiredColumn_ThrowsValidationError() {
        String csv = """
                title,description,start_at,timezone
                "Thiếu cột end_at","Ghi chú",2026-11-28T01:00:00Z,Asia/Ho_Chi_Minh
                """;

        AppException ex = assertThrows(
                AppException.class,
                () -> parser.parseCsv(csv.getBytes(StandardCharsets.UTF_8), "Asia/Ho_Chi_Minh")
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Dòng 1"));
    }

    @Test
    @DisplayName("parseCsv throws 400 VALIDATION_ERROR with exact line number on malformed date")
    void parseCsv_InvalidDateOnLine3_ThrowsValidationErrorWithLineNumber() {
        String csv = """
                title,description,start_at,end_at,timezone,location
                "Hợp lệ","OK",2026-11-28T01:00:00Z,2026-11-28T02:00:00Z,Asia/Ho_Chi_Minh,"P1"
                "Sai ngày","Lỗi",not-a-date,2026-11-28T04:00:00Z,Asia/Ho_Chi_Minh,"P2"
                """;

        AppException ex = assertThrows(
                AppException.class,
                () -> parser.parseCsv(csv.getBytes(StandardCharsets.UTF_8), "Asia/Ho_Chi_Minh")
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Dòng 3"));
    }
}
