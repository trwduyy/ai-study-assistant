package com.aistudy.assistant.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generates or propagates a sanitized per-request {@code traceId} into SLF4J MDC and {@code X-Trace-Id} response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Pattern SAFE_TRACE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String traceId = resolveOrGenerateTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    public static String currentTraceId() {
        String existing = MDC.get(TRACE_ID_MDC_KEY);
        return (existing != null && !existing.isBlank()) ? existing : generateTraceId();
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String resolveOrGenerateTraceId(String incomingTraceId) {
        if (incomingTraceId != null) {
            String trimmed = incomingTraceId.trim();
            if (SAFE_TRACE_ID_PATTERN.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return generateTraceId();
    }
}
