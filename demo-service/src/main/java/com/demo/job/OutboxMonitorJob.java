package com.demo.job;

import com.demo.mapper.MessageOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Day14 - Outbox 监控任务（最小可观测）
 *
 * 职责：
 * 1) 定时统计 Outbox 的 NEW / SENT / FAIL 数量
 * 2) 统计 FAIL 的重试次数总和
 * 3) 达到阈值时输出 ERROR 作为“告警信号”
 */
@Slf4j
@Component
public class OutboxMonitorJob {

    @Autowired
    private MessageOutboxMapper messageOutboxMapper;

    /** 失败条数告警阈值（可按需调整） */
    @Value("${outbox.monitor.fail-threshold:5}")
    private int failThreshold;

    /** 失败重试次数告警阈值（可按需调整） */
    @Value("${outbox.monitor.fail-retry-threshold:10}")
    private int failRetryThreshold;

    /**
     * 每 30 秒输出一次监控日志
     */
    @Scheduled(fixedDelay = 30000)
    public void logOutboxMetrics() {
        int newCount = messageOutboxMapper.countByStatus("NEW");
        int sentCount = messageOutboxMapper.countByStatus("SENT");
        int failCount = messageOutboxMapper.countByStatus("FAIL");
        int failRetrySum = messageOutboxMapper.sumRetryCountByStatus("FAIL");

        // 1) 常规监控日志（INFO）
        log.info("Outbox 监控指标：new={}, sent={}, fail={}, failRetrySum={}",
                newCount, sentCount, failCount, failRetrySum);

        // 2) 失败阈值告警（ERROR）
        if (failCount >= failThreshold || failRetrySum >= failRetryThreshold) {
            log.error("Outbox 告警：failCount={}, failRetrySum={}, failThreshold={}, retryThreshold={}",
                    failCount, failRetrySum, failThreshold, failRetryThreshold);
        } else {
            log.debug("Outbox 告警阈值未触发：failCount={}, failRetrySum={}", failCount, failRetrySum);
        }
    }
}
