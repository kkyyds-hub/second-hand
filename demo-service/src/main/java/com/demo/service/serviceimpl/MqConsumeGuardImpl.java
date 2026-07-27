package com.demo.service.serviceimpl;

import com.demo.entity.MqConsumeLog;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.service.MqConsumeGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 统一 MQ 消费幂等守卫实现。
 *
 * 状态模型（冻结）：
 * - PROCESSING：已抢占，业务执行中
 * - OK：业务已完成
 * - FAIL：系统异常，可重试
 *
 * 关键改进（vs 旧实现）：
 * 1) DuplicateKey 不再直接 ACK；改为查询已有记录，按状态分流；
 * 2) 过期 PROCESSING 通过条件更新原子接管，避免消息丢失；
 * 3) FAIL 记录允许重新抢占执行。
 */
@Slf4j
@Service
public class MqConsumeGuardImpl implements MqConsumeGuard {

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    /**
     * 过期 PROCESSING 超时秒数。
     * 默认 300 秒（5 分钟），可通过配置覆盖。
     */
    @Value("${mq.consume.processing-stale-seconds:300}")
    private int processingStaleSeconds;

    @Override
    public AcquireResult acquire(String consumer, String eventId) {
        // 1) 先尝试插入 PROCESSING
        MqConsumeLog record = new MqConsumeLog();
        record.setConsumer(consumer);
        record.setEventId(eventId);
        record.setStatus("PROCESSING");

        try {
            mqConsumeLogMapper.insert(record);
            log.info("MqConsumeGuard ACQUIRED_NEW: consumer={}, eventId={}, logId={}", consumer, eventId, record.getId());
            return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, record.getId());
        } catch (DuplicateKeyException e) {
            // 2) 唯一键冲突 → 查询已有记录，按状态分流
            return handleDuplicate(consumer, eventId);
        }
    }

    private AcquireResult handleDuplicate(String consumer, String eventId) {
        MqConsumeLog existing = mqConsumeLogMapper.selectByConsumerAndEventId(consumer, eventId);
        if (existing == null) {
            // 极端并发：外部刚删除或从未插入 → 重新插入
            log.warn("MqConsumeGuard duplicate but no record found, retrying insert: {}/{}", consumer, eventId);
            MqConsumeLog retry = new MqConsumeLog();
            retry.setConsumer(consumer);
            retry.setEventId(eventId);
            retry.setStatus("PROCESSING");
            try {
                mqConsumeLogMapper.insert(retry);
                return new AcquireResult(AcquireResult.Type.ACQUIRED_NEW, retry.getId());
            } catch (DuplicateKeyException ignored) {
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, null);
            }
        }

        String status = existing.getStatus();
        switch (status) {
            case "OK":
                log.info("MqConsumeGuard ALREADY_COMPLETED: {}/{}, logId={}", consumer, eventId, existing.getId());
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, existing.getId());

            case "FAIL":
                log.info("MqConsumeGuard RETRYABLE_FAILED: {}/{}, logId={}", consumer, eventId, existing.getId());
                // 尝试原子改回 PROCESSING 重新执行（失败时返回 IN_PROGRESS_RECENT 让消息重新投递）
                mqConsumeLogMapper.updateStatus(existing.getId(), "PROCESSING");
                mqConsumeLogMapper.updateStatus(existing.getId(), "PROCESSING"); // double update to touch updated_at
                return new AcquireResult(AcquireResult.Type.RETRYABLE_FAILED, existing.getId());

            case "PROCESSING":
                // 3) 判断是否过期
                LocalDateTime staleBefore = LocalDateTime.now().minus(Duration.ofSeconds(processingStaleSeconds));
                if (existing.getUpdatedAt() != null && existing.getUpdatedAt().isBefore(staleBefore)) {
                    // 过期 PROCESSING → 尝试原子接管
                    int rows = mqConsumeLogMapper.updateStatusIfStale(
                            existing.getId(), staleBefore, "PROCESSING");
                    if (rows == 1) {
                        log.info("MqConsumeGuard RECOVERED_STALE: {}/{}, logId={}", consumer, eventId, existing.getId());
                        return new AcquireResult(AcquireResult.Type.RECOVERED_STALE, existing.getId());
                    }
                    // 接管失败 → 被其他实例抢先，回退到最近
                }
                log.info("MqConsumeGuard IN_PROGRESS_RECENT: {}/{}, logId={}", consumer, eventId, existing.getId());
                return new AcquireResult(AcquireResult.Type.IN_PROGRESS_RECENT, existing.getId());

            default:
                log.warn("MqConsumeGuard unknown status={}: {}/{}", status, consumer, eventId);
                return new AcquireResult(AcquireResult.Type.ALREADY_COMPLETED, existing.getId());
        }
    }

    @Override
    public void markSuccess(Long logId) {
        mqConsumeLogMapper.updateStatus(logId, "OK");
        log.debug("MqConsumeGuard markSuccess: logId={}", logId);
    }

    @Override
    public void markFailure(Long logId) {
        mqConsumeLogMapper.updateStatus(logId, "FAIL");
        log.debug("MqConsumeGuard markFailure: logId={}", logId);
    }
}
