package com.demo.sms;

import com.demo.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Locale;

/**
 * 短信 sender 工厂。
 *
 * 当前策略：
 * 1) 默认使用 mock；
 * 2) 未识别 provider 或 provider 暂不可用时回退到 mock；
 * 3) 启动时打印实际加载的 sender，便于排查配置问题。
 */
@Slf4j
@Component
public class SmsSenderFactory {

    private final SmsProperties smsProperties;
    private final MockSmsSender mockSmsSender;

    public SmsSenderFactory(SmsProperties smsProperties, MockSmsSender mockSmsSender) {
        this.smsProperties = smsProperties;
        this.mockSmsSender = mockSmsSender;
    }

    @PostConstruct
    public void logSender() {
        log.info("sms sender loaded: {}", getSender().getName());
    }

    public SmsSender getSender() {
        String provider = smsProperties.getProvider();
        if (provider == null) {
            return mockSmsSender;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "mock":
                if (smsProperties.getMock() != null && !smsProperties.getMock().isEnabled()) {
                    log.warn("sms mock sender disabled but no real sender is implemented, fallback to mock");
                }
                return mockSmsSender;
            default:
                log.warn("unknown sms provider: {}, fallback to mock", provider);
                return mockSmsSender;
        }
    }
}
