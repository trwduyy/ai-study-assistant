package com.aistudy.assistant.schedule.integration;

import java.time.Instant;
import java.util.List;

/**
 * Read-only Google OAuth2 and Google Calendar v3 REST client.
 * Must always be invoked outside database transactions.
 */
public interface GoogleCalendarClient {

    String READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly";

    String buildAuthorizeUrl(String state);

    GoogleTokenResponse exchangeAuthorizationCode(String code);

    String refreshAccessToken(String refreshToken);

    List<GoogleCalendarEventItem> fetchPrimaryEvents(String accessToken, Instant timeMin, Instant timeMax);
}
