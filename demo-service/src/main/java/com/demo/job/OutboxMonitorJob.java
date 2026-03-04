package com.demo.job;

import com.demo.mapper.MessageOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
     * 是否在监控口径中排除测试交换机。
     *
     * 背景：P7-S2 会注入 bad.exchange 失败样本用于演练，若不排除，
     * 单条残留 FAIL 事件会持续抬高 retry_sum，长期污染生产告警。
     */
    @Value("${outbox.monitor.exclude-test-exchanges-enabled:true}")
    private boolean excludeTestExchangesEnabled;

    /**
     * 逗号分隔的交换机黑名单（监控排除口径）。
     * 默认保留 Day18 失败注入交换机 bad.exchange。
     */
    @Value("${outbox.monitor.exclude-exchanges:bad.exchange}")
    private String excludeExchangesRaw;

    /**
     * 每 30 秒输出一次监控日志
     */
    @Scheduled(fixedDelay = 30000)
    public void logOutboxMetrics() {
        List<String> excludeExchanges = resolveExcludeExchanges();
        int newCount = messageOutboxMapper.countByStatusExcludeExchanges("NEW", excludeExchanges);
        int sentCount = messageOutboxMapper.countByStatusExcludeExchanges("SENT", excludeExchanges);
        int failCount = messageOutboxMapper.countByStatusExcludeExchanges("FAIL", excludeExchanges);
        int failRetrySum = messageOutboxMapper.sumRetryCountByStatusExcludeExchanges("FAIL", excludeExchanges);

        // 1) 常规监控日志（INFO）
        log.info("Outbox 监控指标：new={}, sent={}, fail={}, failRetrySum={}, excludeExchanges={}",
                newCount, sentCount, failCount, failRetrySum, excludeExchanges);

        // 2) 失败阈值告警（ERROR）
        if (failCount >= failThreshold || failRetrySum >= failRetryThreshold) {
            log.error("Outbox 告警：failCount={}, failRetrySum={}, failThreshold={}, retryThreshold={}",
                    failCount, failRetrySum, failThreshold, failRetryThreshold);
        } else {
            log.debug("Outbox 告警阈值未触发：failCount={}, failRetrySum={}", failCount, failRetrySum);
        }
    }

    private List<String> resolveExcludeExchanges() {
        if (!excludeTestExchangesEnabled) {
            return Collections.emptyList();
        }
        if (excludeExchangesRaw == null || excludeExchangesRaw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(excludeExchangesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
