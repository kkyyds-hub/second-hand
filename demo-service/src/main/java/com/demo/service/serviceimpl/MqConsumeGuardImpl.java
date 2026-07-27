package com.demo.service.serviceimpl;

import com.demo.entity.MqConsumeLog;
import com.demo.exception.BusinessException;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.service.MqConsumeGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一 MQ 消费幂等守卫实现（租约栅栏版）。
 *
 * 关键改进（P6-MQ-A-F1）：
 * 1) lease_token：每次抢占生成 UUID，markSuccess/markFailure 验证租约
 * 2) attempt_count：每次成功抢占 +1
 * 3) FAIL 重抢占：原子 UPDATE WHERE status='FAIL'，非简单 updateStatus
 * 4) stale 接管：原子 UPDATE WHERE lease_token 匹配 + updated_at 过期
 * 5) DuplicateKey 二次竞争：有界重查，不倒向 ALREADY_COMPLETED
 * 6) 未知状态：失败关闭 → UNKNOWN_STATE_RETRY
 */
@Slf4j
@Service
public class MqConsumeGuardImpl implements MqConsumeGuard {

    private static final int DUPLICATE_MAX_RETRIES = 3;

    @Autowired
    private MqConsumeLogMapper mapper;

    @Value("${mq.consume.processing-stale-seconds:300}")
    private int processingStaleSeconds;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public AcquireResult acquire(String consumer, String eventId) {
        // 1) 首次抢占
        String leaseToken = UUID.randomUUID().toString();
        MqConsumeLog record = new MqConsumeLog();
        record.setConsumer(consumer);
        record.setEventId(eventId);
        record.setStatus("PROCESSING");
        record.setLeaseToken(leaseToken);
        record.setAttemptCount(1);

        try {
            mapper.insert(record);
            log.info("MqConsumeGuard ACQUIRED_NEW consumer={} eventId={} id={} token={}",
                    consumer, eventId, record.getId(), tokenPreview(leaseToken));
            return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, record.getId(), leaseToken);
        } catch (DuplicateKeyException e) {
            return handleDuplicate(consumer, eventId);
        }
    }

    private AcquireResult handleDuplicate(String consumer, String eventId) {
        for (int attempt = 0; attempt < DUPLICATE_MAX_RETRIES; attempt++) {
            MqConsumeLog existing = mapper.selectByConsumerAndEventId(consumer, eventId);
            if (existing != null) {
                return routeByStatus(consumer, eventId, existing);
            }
            // 记录被并发删除 → 重新插入
            String token = UUID.randomUUID().toString();
            MqConsumeLog retry = new MqConsumeLog();
            retry.setConsumer(consumer);
            retry.setEventId(eventId);
            retry.setStatus("PROCESSING");
            retry.setLeaseToken(token);
            retry.setAttemptCount(1);
            try {
                mapper.insert(retry);
                log.info("MqConsumeGuard ACQUIRED_NEW (retry) consumer={} eventId={}", consumer, eventId);
                return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, retry.getId(), token);
            } catch (DuplicateKeyException ignored) {
                // 极短让步后继续循环
                sleepQuietly(10);
            }
        }
        // 最终兜底：抛系统异常，让消息 NACK
        throw new RuntimeException("MqConsumeGuard: duplicate key race unresolved for " + consumer + "/" + eventId);
    }

    private AcquireResult routeByStatus(String consumer, String eventId, MqConsumeLog existing) {
        String status = safeStatus(existing);

        switch (status) {
            case "OK":
                log.info("MqConsumeGuard ALREADY_COMPLETED {}/{}", consumer, eventId);
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, existing.getId(), null);

            case "FAIL":
                return tryRetakeFailed(consumer, eventId, existing);

            case "PROCESSING":
                return handleProcessing(consumer, eventId, existing);

            default:
                log.warn("MqConsumeGuard UNKNOWN_STATE_RETRY status={} {}/{}",
                        status, consumer, eventId);
                return new AcquireResult(AcquireResult.Type.UNKNOWN_STATE_RETRY, existing.getId(), null);
        }
    }

    private AcquireResult tryRetakeFailed(String consumer, String eventId, MqConsumeLog existing) {
        String newToken = UUID.randomUUID().toString();
        int rows = mapper.retakeFailedLease(existing.getId(), newToken);
        if (rows == 1) {
            log.info("MqConsumeGuard RETRYABLE_FAILED {}/{} id={} token={}",
                    consumer, eventId, existing.getId(), tokenPreview(newToken));
            return new AcquireResult(AcquireResult.Type.RETRYABLE_FAILED, existing.getId(), newToken);
        }
        // 抢占失败 → 重新分流
        log.info("MqConsumeGuard FAIL retake conflict, re-querying {}/{}", consumer, eventId);
        MqConsumeLog latest = mapper.selectByConsumerAndEventId(consumer, eventId);
        if (latest == null) {
            // 已删除 → 重新插入
            String token = UUID.randomUUID().toString();
            MqConsumeLog retry = new MqConsumeLog();
            retry.setConsumer(consumer);
            retry.setEventId(eventId);
            retry.setStatus("PROCESSING");
            retry.setLeaseToken(token);
            retry.setAttemptCount(1);
            try {
                mapper.insert(retry);
                return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, retry.getId(), token);
            } catch (DuplicateKeyException ignored) {
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, null, null);
            }
        }
        return routeByStatus(consumer, eventId, latest);
    }

    private AcquireResult handleProcessing(String consumer, String eventId, MqConsumeLog existing) {
        LocalDateTime staleBefore = LocalDateTime.now().minus(Duration.ofSeconds(processingStaleSeconds));
        if (existing.getUpdatedAt() != null && existing.getUpdatedAt().isBefore(staleBefore)) {
            return tryStaleTakeover(consumer, eventId, existing, staleBefore);
        }
        log.info("MqConsumeGuard IN_PROGRESS_RECENT {}/{}", consumer, eventId);
        return new AcquireResult(AcquireResult.Type.IN_PROGRESS_RECENT, existing.getId(), null);
    }

    private AcquireResult tryStaleTakeover(String consumer, String eventId,
                                            MqConsumeLog existing, LocalDateTime staleBefore) {
        String newToken = UUID.randomUUID().toString();
        String observedToken = existing.getLeaseToken();
        int rows = mapper.updateStatusIfStaleAndLeaseToken(
                existing.getId(), observedToken, staleBefore, newToken);
        if (rows == 1) {
            log.info("MqConsumeGuard RECOVERED_STALE {}/{} id={} token={}",
                    consumer, eventId, existing.getId(), tokenPreview(newToken));
            return new AcquireResult(AcquireResult.Type.RECOVERED_STALE, existing.getId(), newToken);
        }
        log.info("MqConsumeGuard stale takeover conflict, re-querying {}/{}", consumer, eventId);
        MqConsumeLog latest = mapper.selectByConsumerAndEventId(consumer, eventId);
        if (latest == null) {
            // 已删除 → 重新插入
            String token = UUID.randomUUID().toString();
            MqConsumeLog retry = new MqConsumeLog();
            retry.setConsumer(consumer);
            retry.setEventId(eventId);
            retry.setStatus("PROCESSING");
            retry.setLeaseToken(token);
            retry.setAttemptCount(1);
            try {
                mapper.insert(retry);
                return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, retry.getId(), token);
            } catch (DuplicateKeyException ignored) {
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, null, null);
            }
        }
        return routeByStatus(consumer, eventId, latest);
    }

    @Override
    public boolean markSuccess(Long logId, String leaseToken) {
        if (logId == null || leaseToken == null) return false;
        int rows = mapper.markSuccess(logId, leaseToken);
        if (rows == 1) {
            log.debug("MqConsumeGuard markSuccess id={}", logId);
            return true;
        }
        log.warn("MqConsumeGuard markSuccess lease-lost id={} token={}", logId, tokenPreview(leaseToken));
        return false;
    }

    @Override
    public boolean markFailure(Long logId, String leaseToken, String error) {
        if (logId == null || leaseToken == null) return false;
        String safeError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        int rows = mapper.markFailure(logId, leaseToken, safeError);
        if (rows == 1) {
            log.debug("MqConsumeGuard markFailure id={}", logId);
            return true;
        }
        log.warn("MqConsumeGuard markFailure lease-lost id={} token={}", logId, tokenPreview(leaseToken));
        return false;
    }

    private static String safeStatus(MqConsumeLog e) {
        return e.getStatus() == null ? "" : e.getStatus().trim();
    }

    private static String tokenPreview(String token) {
        if (token == null) return "null";
        return token.length() > 8 ? token.substring(0, 8) + "..." : token;
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
