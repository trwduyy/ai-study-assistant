package com.aistudy.assistant.common;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.common.entity.BaseFullEntity;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.exception.GlobalExceptionHandler;
import com.aistudy.assistant.common.filter.TraceIdFilter;
import jakarta.servlet.FilterChain;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonFoundationTest {

    @SuperBuilder
    static class DummyFullEntity extends BaseFullEntity {
        private String title;
    }

    @Test
    @DisplayName("TraceIdFilter sets X-Trace-Id header, populates MDC during chain, and clears MDC afterwards")
    void traceIdFilter_GeneratesAndCleansUpMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedMdcTraceId = new String[1];
        FilterChain chain = (req, res) -> capturedMdcTraceId[0] = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedMdcTraceId[0]);
        assertEquals(capturedMdcTraceId[0], response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("TraceIdFilter sanitizes invalid incoming X-Trace-Id headers")
    void traceIdFilter_RejectsUnsafeIncomingTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "bad\r\nheader value!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String header = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(header);
        assertNotEquals("bad\r\nheader value!", header);
        assertTrue(header.matches("^[a-zA-Z0-9-]{8,64}$"));
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps AppException and hides internal details on unexpected errors")
    void globalExceptionHandler_MapsErrorsAndHidesInternals() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "trace1234567890a");

        try {
            ResponseEntity<ApiResponse<Void>> appRes = handler.handleAppException(
                    new AppException(ErrorCode.QUIZ_ALREADY_SUBMITTED),
                    request
            );
            assertEquals(HttpStatus.CONFLICT, appRes.getStatusCode());
            assertNotNull(appRes.getBody());
            assertFalse(appRes.getBody().isSuccess());
            assertEquals("QUIZ_ALREADY_SUBMITTED", appRes.getBody().getError().getCode());
            assertEquals("trace1234567890a", appRes.getBody().getTraceId());

            ResponseEntity<ApiResponse<Void>> lockRes = handler.handleOptimisticLock(
                    new ObjectOptimisticLockingFailureException("Course", 1L),
                    request
            );
            assertEquals(HttpStatus.CONFLICT, lockRes.getStatusCode());
            assertEquals("VERSION_CONFLICT", lockRes.getBody().getError().getCode());

            ResponseEntity<ApiResponse<Void>> dbRes = handler.handleDataIntegrityViolation(
                    new DataIntegrityViolationException("Duplicate entry 'x' for key 'uk_users_email'"),
                    request
            );
            assertEquals(HttpStatus.CONFLICT, dbRes.getStatusCode());
            assertFalse(dbRes.getBody().getError().getMessage().contains("uk_users_email"));

            ResponseEntity<ApiResponse<Void>> unexpectedRes = handler.handleUnexpectedException(
                    new RuntimeException("SQL syntax error near table users"),
                    request
            );
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, unexpectedRes.getStatusCode());
            assertEquals("INTERNAL_ERROR", unexpectedRes.getBody().getError().getCode());
            assertFalse(unexpectedRes.getBody().getError().getMessage().contains("SQL"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("BaseFullEntity supports soft delete lifecycle and default optimistic version")
    void baseFullEntity_SoftDeleteAndVersion() {
        DummyFullEntity entity = DummyFullEntity.builder()
                .id(1L)
                .title("Test")
                .build();

        assertEquals(0, entity.getVersion());
        assertFalse(entity.isDeleted());

        entity.markDeleted();
        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());

        entity.restore();
        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
    }

    @Test
    @DisplayName("PageMeta builds offset and cursor metadata accurately")
    void pageMeta_FactoryMethods() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 42);
        PageMeta offsetMeta = PageMeta.ofPage(page);
        assertEquals(0, offsetMeta.getPage());
        assertEquals(20, offsetMeta.getSize());
        assertEquals(42L, offsetMeta.getTotalElements());
        assertEquals(3, offsetMeta.getTotalPages());
        assertNull(offsetMeta.getNextCursor());

        PageMeta cursorMeta = PageMeta.ofCursor(30, "990");
        assertEquals(30, cursorMeta.getSize());
        assertEquals("990", cursorMeta.getNextCursor());
        assertNull(cursorMeta.getPage());
    }
}
