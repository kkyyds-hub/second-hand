package com.demo.logistics;

import com.demo.config.LogisticsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Locale;

/**
 * 物流 provider 工厂。
 *
 * 职责：
 * 1) 根据配置 logistics.provider 选择具体实现
 * 2) 对未知配置自动回退 mock，确保系统可用性
 */
@Slf4j
@Component
public class LogisticsProviderFactory {

    private final LogisticsProperties logisticsProperties;
    private final MockLogisticsProvider mockLogisticsProvider;
    private final DeliveryTrackerProvider deliveryTrackerProvider;

    public LogisticsProviderFactory(LogisticsProperties logisticsProperties,
                                    MockLogisticsProvider mockLogisticsProvider,
                                    DeliveryTrackerProvider deliveryTrackerProvider) {
        this.logisticsProperties = logisticsProperties;
        this.mockLogisticsProvider = mockLogisticsProvider;
        this.deliveryTrackerProvider = deliveryTrackerProvider;
    }

    /**
     * 启动时打印实际加载 provider，便于排查“配置未生效”问题。
     */
    @PostConstruct
    public void logProvider() {
        log.info("logistics provider loaded: {}", getProvider().getName());
    }

    /**
     * 选择 provider。
     *
     * 支持别名：
     * - delivery-tracker / delivery_tracker / deliverytracker
     * - mock
     *
     * 未识别值自动回退 mock（fail-safe）。
     */
    public LogisticsProvider getProvider() {
        String provider = logisticsProperties.getProvider();
        if (provider == null) {
            return mockLogisticsProvider;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "delivery-tracker":
            case "delivery_tracker":
            case "deliverytracker":
                if (canUseDeliveryTracker()) {
                    return deliveryTrackerProvider;
                }
                log.warn("delivery-tracker not ready, fallback to mock");
                return mockLogisticsProvider;
            case "mock":
            default:
                if (!"mock".equals(normalized)) {
                    log.warn("unknown logistics provider: {}, fallback to mock", provider);
                }
                return mockLogisticsProvider;
        }
    }

    /**
     * 显式获取 mock provider。
     * 用途：服务层在三方调用异常时做二次兜底。
     */
    public LogisticsProvider getMockProvider() {
        return mockLogisticsProvider;
    }

    /**
     * 判断 delivery-tracker 是否达到可用条件。
     * 条件：
     * 1) enabled=true
     * 2) baseUrl 非空
     * 3) apiKey 非空
     */
    private boolean canUseDeliveryTracker() {
        LogisticsProperties.DeliveryTracker cfg = logisticsProperties.getDeliveryTracker();
        if (cfg == null) {
            return false;
        }
        return cfg.isEnabled()
                && notBlank(cfg.getBaseUrl())
                && notBlank(cfg.getApiKey());
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
