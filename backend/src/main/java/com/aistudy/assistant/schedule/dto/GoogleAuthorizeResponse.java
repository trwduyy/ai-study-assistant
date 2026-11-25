package com.aistudy.assistant.schedule.dto;

import java.util.List;

public record GoogleAuthorizeResponse(
        String authorizeUrl,
        List<String> scopes
) {
}
