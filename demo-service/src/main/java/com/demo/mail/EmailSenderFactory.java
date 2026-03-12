package com.demo.mail;

import com.demo.config.EmailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Locale;

/**
 * 邮件 sender 工厂。
 */
@Slf4j
@Component
public class EmailSenderFactory {

    private final EmailProperties emailProperties;
    private final MockEmailSender mockEmailSender;
    private final SmtpEmailSender smtpEmailSender;

    public EmailSenderFactory(EmailProperties emailProperties,
                              MockEmailSender mockEmailSender,
                              SmtpEmailSender smtpEmailSender) {
        this.emailProperties = emailProperties;
        this.mockEmailSender = mockEmailSender;
        this.smtpEmailSender = smtpEmailSender;
    }

    @PostConstruct
    public void logSender() {
        log.info("email sender loaded: {}", getSender().getName());
    }

    public EmailSender getSender() {
        String provider = emailProperties.getProvider();
        if (provider == null) {
            return mockEmailSender;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "smtp":
                if (!smtpEmailSender.isAvailable()) {
                    log.warn("smtp email sender unavailable, fallback to mock");
                    return mockEmailSender;
                }
                return smtpEmailSender;
            case "mock":
                if (emailProperties.getMock() != null && !emailProperties.getMock().isEnabled()) {
                    log.warn("email mock sender disabled but no other available sender is configured, fallback to mock");
                }
                return mockEmailSender;
            default:
                log.warn("unknown email provider: {}, fallback to mock", provider);
                return mockEmailSender;
        }
    }
}
