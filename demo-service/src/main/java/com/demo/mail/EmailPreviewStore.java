package com.demo.mail;

import com.demo.config.EmailProperties;
import com.demo.json.JacksonObjectMapper;
import com.demo.vo.EmailPreviewVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 邮件预览存储。
 */
@Slf4j
@Component
public class EmailPreviewStore {

    private static final String EMAIL_PREVIEW_LATEST_KEY_PREFIX = "auth:email:preview:latest:";
    private static final String EMAIL_PREVIEW_RECENT_KEY = "auth:email:preview:recent";
    private static final Duration EMAIL_PREVIEW_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final EmailProperties emailProperties;
    private final ObjectMapper objectMapper = new JacksonObjectMapper();

    public EmailPreviewStore(StringRedisTemplate stringRedisTemplate, EmailProperties emailProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.emailProperties = emailProperties;
    }

    public EmailPreviewVO save(ActivationMail activationMail, String provider) {
        EmailPreviewVO preview = new EmailPreviewVO();
        preview.setPreviewId(UUID.randomUUID().toString().replace("-", ""));
        preview.setEmail(activationMail.getEmail());
        preview.setSubject(activationMail.getSubject());
        preview.setContent(activationMail.getContent());
        preview.setToken(activationMail.getToken());
        preview.setActivationUrl(activationMail.getActivationUrl());
        preview.setProvider(provider);
        preview.setSentAt(LocalDateTime.now());
        preview.setExpireAt(activationMail.getExpireAt());

        String json = toJson(preview);
        String latestKey = EMAIL_PREVIEW_LATEST_KEY_PREFIX + normalizeEmail(activationMail.getEmail());
        stringRedisTemplate.opsForValue().set(latestKey, json, EMAIL_PREVIEW_TTL);
        stringRedisTemplate.opsForList().leftPush(EMAIL_PREVIEW_RECENT_KEY, json);
        stringRedisTemplate.opsForList().trim(EMAIL_PREVIEW_RECENT_KEY, 0, resolveRecentLimit() - 1L);
        stringRedisTemplate.expire(EMAIL_PREVIEW_RECENT_KEY, EMAIL_PREVIEW_TTL);
        return preview;
    }

    public EmailPreviewVO getLatest(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(EMAIL_PREVIEW_LATEST_KEY_PREFIX + normalizeEmail(email));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return parse(json);
    }

    public List<EmailPreviewVO> listRecent(int limit) {
        int actualLimit = Math.max(1, Math.min(limit, resolveRecentLimit()));
        List<String> values = stringRedisTemplate.opsForList().range(EMAIL_PREVIEW_RECENT_KEY, 0, actualLimit - 1L);
        List<EmailPreviewVO> result = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return result;
        }
        for (String value : values) {
            EmailPreviewVO preview = parse(value);
            if (preview != null) {
                result.add(preview);
            }
        }
        return result;
    }

    private int resolveRecentLimit() {
        if (emailProperties.getMock() == null || emailProperties.getMock().getRecentLimit() <= 0) {
            return 20;
        }
        return emailProperties.getMock().getRecentLimit();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String toJson(EmailPreviewVO preview) {
        try {
            return objectMapper.writeValueAsString(preview);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("邮件预览序列化失败", ex);
        }
    }

    private EmailPreviewVO parse(String json) {
        try {
            return objectMapper.readValue(json, EmailPreviewVO.class);
        } catch (IOException ex) {
            log.warn("邮件预览反序列化失败，已跳过异常记录");
            return null;
        }
    }
}
