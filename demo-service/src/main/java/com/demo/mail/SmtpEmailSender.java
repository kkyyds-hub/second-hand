package com.demo.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SMTP 邮件发送器。
 */
@Slf4j
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String mailFrom;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                           @Value("${spring.mail.username:}") String mailFrom) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailFrom = mailFrom;
    }

    @Override
    public String getName() {
        return "smtp";
    }

    public boolean isAvailable() {
        return mailSender != null && StringUtils.hasText(mailFrom);
    }

    @Override
    public void sendActivationMail(ActivationMail activationMail) {
        if (!isAvailable()) {
            throw new IllegalStateException("SMTP 邮件发送器不可用");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(activationMail.getEmail());
        message.setSubject(activationMail.getSubject());
        message.setText(activationMail.getContent());
        mailSender.send(message);
        log.info("activation mail sent via smtp: email={}, expireAt={}",
                maskEmail(activationMail.getEmail()),
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
