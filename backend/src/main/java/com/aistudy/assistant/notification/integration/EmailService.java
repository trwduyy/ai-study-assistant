package com.aistudy.assistant.notification.integration;

/**
 * Outbound SMTP email delivery adapter invoked outside database transactions.
 */
public interface EmailService {

    void sendEmail(String to, String subject, String body);
}
