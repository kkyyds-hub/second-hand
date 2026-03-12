package com.demo.mail;

import com.demo.config.EmailProperties;
import com.demo.vo.EmailPreviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Mock 邮件发送器。
 */
@Slf4j
@Component
public class MockEmailSender implements EmailSender {

    private final EmailProperties emailProperties;
    private final EmailPreviewStore emailPreviewStore;

    public MockEmailSender(EmailProperties emailProperties, EmailPreviewStore emailPreviewStore) {
        this.emailProperties = emailProperties;
        this.emailPreviewStore = emailPreviewStore;
    }

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public void sendActivationMail(ActivationMail activationMail) {
        EmailPreviewVO preview = null;
        if (emailProperties.getMock() != null && emailProperties.getMock().isStorePreview()) {
            preview = emailPreviewStore.save(activationMail, getName());
        }
        if (emailProperties.getMock() != null && emailProperties.getMock().isLogActivationUrl()) {
            log.info("mock activation mail generated: email={}, previewId={}, activationUrl={}, expireAt={}",
                    maskEmail(activationMail.getEmail()),
                    preview == null ? "NONE" : preview.getPreviewId(),
                    activationMail.getActivationUrl(),
                    activationMail.getExpireAt());
            return;
        }
        log.info("mock activation mail generated: email={}, previewId={}, expireAt={}",
                maskEmail(activationMail.getEmail()),
                preview == null ? "NONE" : preview.getPreviewId(),
                activationMail.getExpireAt());
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "EMPTY";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***@" + (at >= 0 ? trimmed.substring(at + 1) : "***");
        }
        return trimmed.substring(0, 1) + "***@" + trimmed.substring(at + 1);
    }
}
