package com.demo.service;

import com.demo.entity.MqConsumeLog;

/**
 * 统一 MQ 消费幂等守卫。
 *
 * 语义：
 * - ACQUIRED_NEW:          首次插入 PROCESSING 成功，当前实例执行业务
 * - ALREADY_COMPLETED:     已有 OK 记录，消息已完成处理，直接 ACK
 * - IN_PROGRESS_RECENT:    已有最近 PROCESSING（其他实例正在处理），NACK + requeue
 * - RECOVERED_STALE:       过期 PROCESSING 被当前实例原子接管，继续执行业务
 * - RETRYABLE_FAILED:      已有 FAIL 记录，尝试重新抢占执行业务
 */
public interface MqConsumeGuard {

    /**
     * 尝试为当前消息获取消费权。
     *
     * @param consumer 消费者名称（幂等键的一部分）
     * @param eventId  事件唯一 ID（幂等键的一部分）
     * @return 获取结果，包含 logId 和结果类型
     */
    AcquireResult acquire(String consumer, String eventId);

    /**
     * 标记消费成功（业务完成）。
     */
    void markSuccess(Long logId);

    /**
     * 标记消费失败（系统异常，后续可重试）。
     */
    void markFailure(Long logId);

    /**
     * 获取结果。
     */
    class AcquireResult {
        private final Type type;
        private final Long logId;

        public AcquireResult(Type type, Long logId) {
            this.type = type;
            this.logId = logId;
        }

        public Type type() { return type; }
        public Long logId() { return logId; }

        public boolean shouldExecute() {
            return type == Type.ACQUIRED_NEW
                || type == Type.RECOVERED_STALE
                || type == Type.RETRYABLE_FAILED;
        }

        public enum Type {
            ACQUIRED_NEW,
            ALREADY_COMPLETED,
            IN_PROGRESS_RECENT,
            RECOVERED_STALE,
            RETRYABLE_FAILED,
        }
    }
}
