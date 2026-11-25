package com.aistudy.assistant.identity.security;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.filter.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles unauthenticated requests (HTTP 401) using the V2 {@link ApiResponse} format with {@code traceId}.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String traceId = TraceIdFilter.currentTraceId();
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> errorResponse = ApiResponse.error(
                ErrorCode.UNAUTHENTICATED,
                ErrorCode.UNAUTHENTICATED.getDefaultMessage(),
                traceId
        );
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
