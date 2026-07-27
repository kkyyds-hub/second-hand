package com.demo.service.serviceimpl;

import com.demo.entity.MqConsumeLog;
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
 * P6-MQ-A-F2 统一 MQ 消费幂等守卫（统一有界解析循环版）。
 *
 * 变更：
 * - DuplicateKey 后统一走 resolveWithRetry(maxAttempts) 有界循环
 * - 抢占失败/重查为空后不再返回 ALREADY_COMPLETED，回到循环下一轮
 * - NULL lease_token 支持（遗留数据兼容）
 * - markSuccess/retake 清空 last_error
 */
@Slf4j
@Service
public class MqConsumeGuardImpl implements MqConsumeGuard {

    private static final int MAX_RESOLVE_ATTEMPTS = 5;

    @Autowired
    private MqConsumeLogMapper mapper;

    @Value("${mq.consume.processing-stale-seconds:300}")
    private int processingStaleSeconds;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public AcquireResult acquire(String consumer, String eventId) {
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
            return resolveWithRetry(consumer, eventId, MAX_RESOLVE_ATTEMPTS);
        }
    }

    /**
     * 统一有界解析循环。
     * 每轮：查询 → 路由 → 可能抢占 → 抢占失败回到下一轮重新查询。
     * 超过最大次数抛出可重试系统异常。
     */
    private AcquireResult resolveWithRetry(String consumer, String eventId, int remaining) {
        if (remaining <= 0) {
            throw new RuntimeException(
                    "MqConsumeGuard: resolution exhausted for " + consumer + "/" + eventId);
        }

        // 1) 查询
        MqConsumeLog existing = mapper.selectByConsumerAndEventId(consumer, eventId);
        if (existing == null) {
            return reinsertOrRetry(consumer, eventId, remaining);
        }

        // 2) 路由
        String status = existing.getStatus() == null ? "" : existing.getStatus().trim();
        switch (status) {
            case "OK":
                log.info("MqConsumeGuard ALREADY_COMPLETED {}/{}", consumer, eventId);
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, existing.getId(), null);

            case "FAIL":
                return tryRetakeFailed(consumer, eventId, existing, remaining);

            case "PROCESSING":
                return tryHandleProcessing(consumer, eventId, existing, remaining);

            default:
                log.warn("MqConsumeGuard UNKNOWN_STATE_RETRY status={} {}/{}",
                        status, consumer, eventId);
                return new AcquireResult(AcquireResult.Type.UNKNOWN_STATE_RETRY, existing.getId(), null);
        }
    }

    private AcquireResult reinsertOrRetry(String consumer, String eventId, int remaining) {
        String token = UUID.randomUUID().toString();
        MqConsumeLog retry = new MqConsumeLog();
        retry.setConsumer(consumer);
        retry.setEventId(eventId);
        retry.setStatus("PROCESSING");
        retry.setLeaseToken(token);
        retry.setAttemptCount(1);
        try {
            mapper.insert(retry);
            log.info("MqConsumeGuard ACQUIRED_NEW (reinsert) consumer={} eventId={}", consumer, eventId);
            return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, retry.getId(), token);
        } catch (DuplicateKeyException ignored) {
            return resolveWithRetry(consumer, eventId, remaining - 1);
        }
    }

    private AcquireResult tryRetakeFailed(String consumer, String eventId,
                                           MqConsumeLog existing, int remaining) {
        String newToken = UUID.randomUUID().toString();
        int rows = mapper.retakeFailedLease(existing.getId(), newToken);
        if (rows == 1) {
            log.info("MqConsumeGuard RETRYABLE_FAILED {}/{} id={} token={}",
                    consumer, eventId, existing.getId(), tokenPreview(newToken));
            return new AcquireResult(AcquireResult.Type.RETRYABLE_FAILED, existing.getId(), newToken);
        }
        // rows=0 → 被其他线程抢先，下一轮重新查询分流
        return resolveWithRetry(consumer, eventId, remaining - 1);
    }

    private AcquireResult tryHandleProcessing(String consumer, String eventId,
                                               MqConsumeLog existing, int remaining) {
        LocalDateTime staleBefore = LocalDateTime.now().minus(Duration.ofSeconds(processingStaleSeconds));
        if (existing.getUpdatedAt() != null && existing.getUpdatedAt().isBefore(staleBefore)) {
            return tryStaleTakeover(consumer, eventId, existing, staleBefore, remaining);
        }
        log.info("MqConsumeGuard IN_PROGRESS_RECENT {}/{}", consumer, eventId);
        return new AcquireResult(AcquireResult.Type.IN_PROGRESS_RECENT, existing.getId(), null);
    }

    private AcquireResult tryStaleTakeover(String consumer, String eventId,
                                            MqConsumeLog existing, LocalDateTime staleBefore,
                                            int remaining) {
        String newToken = UUID.randomUUID().toString();
        String observedToken = existing.getLeaseToken();
        int rows = mapper.updateStatusIfStaleAndLeaseToken(
                existing.getId(), observedToken, staleBefore, newToken);
        if (rows == 1) {
            log.info("MqConsumeGuard RECOVERED_STALE {}/{} id={} token={}",
                    consumer, eventId, existing.getId(), tokenPreview(newToken));
            return new AcquireResult(AcquireResult.Type.RECOVERED_STALE, existing.getId(), newToken);
        }
        // rows=0 → 被其他线程抢先，下一轮重新查询分流
        return resolveWithRetry(consumer, eventId, remaining - 1);
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

    private static String tokenPreview(String token) {
        if (token == null) return "null";
        return token.length() > 8 ? token.substring(0, 8) + "..." : token;
    }
}
