package com.demo.sms;

import com.demo.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Mock 短信发送器。
 *
 * 特点：
 * 1) 不调用真实短信服务，零成本可演示；
 * 2) 可将验证码打印到日志，便于前后端联调；
 * 3) 保留真实发送抽象，后续可无缝替换。
 */
@Slf4j
@Component
public class MockSmsSender implements SmsSender {

    private final SmsProperties smsProperties;

    public MockSmsSender(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public void sendCode(String mobile, String code, long ttlMinutes) {
        if (smsProperties.getMock() != null && smsProperties.getMock().isLogCode()) {
            log.info("dev sms code generated: mobile={}, code={}, ttlMinutes={}",
                    maskMobile(mobile), code, ttlMinutes);
            return;
        }
        log.info("dev sms code generated: mobile={}, ttlMinutes={}", maskMobile(mobile), ttlMinutes);
    }

    @Override
    public String buildSuccessMessage(long ttlMinutes) {
        return "\u5F00\u53D1\u6A21\u5F0F\u9A8C\u8BC1\u7801\u5DF2\u751F\u6210\uFF0C"
                + ttlMinutes
                + "\u5206\u949F\u5185\u6709\u6548";
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "EMPTY";
        }
        String trimmed = mobile.trim();
        if (trimmed.length() <= 7) {
            return "***";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
