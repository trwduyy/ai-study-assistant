package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.schedule.dto.GoogleAuthorizeResponse;
import com.aistudy.assistant.schedule.dto.GoogleConnectionResponse;
import com.aistudy.assistant.schedule.dto.GoogleSyncRequest;
import com.aistudy.assistant.schedule.dto.GoogleSyncResponse;

public interface GoogleIntegrationService {

    GoogleAuthorizeResponse authorize();

    GoogleConnectionResponse handleCallback(String code, String state);

    GoogleSyncResponse syncCalendar(GoogleSyncRequest request);

    void disconnectGoogle();
}
