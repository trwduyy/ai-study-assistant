package com.aistudy.assistant.notification.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SMTP email sender backed by Spring {@link JavaMailSender}.
 * Skips actual network delivery when {@code app.mail.enabled=false} (default in dev/test).
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean mailEnabled;
    private final String fromAddress;

    public EmailServiceImpl(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:noreply@aistudy.local}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        if (!mailEnabled) {
            log.debug("Mail delivery disabled (app.mail.enabled=false); skipping SMTP email to={} subject='{}'",
                    to, subject);
            return;
        }
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("Recipient email address must not be blank");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender bean is unavailable while app.mail.enabled=true");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Sent notification email to={} subject='{}'", to, subject);
    }
}
