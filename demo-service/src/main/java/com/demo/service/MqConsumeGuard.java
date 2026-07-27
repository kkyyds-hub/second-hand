package com.demo.service;

/**
 * 统一 MQ 消费幂等守卫（P6-MQ-A-F1 租约栅栏版）。
 *
 * 每个成功抢占返回不可伪造的 leaseToken（UUID）。
 * markSuccess / markFailure 必须通过 leaseToken 验证所有权。
 *
 * 状态与所有权：
 * - PROCESSING + lease_token = 当前执行者持有租约
 * - OK + lease_token = 最近完成者（仅用于审计）
 * - FAIL + lease_token = 最近失败者（仅用于审计）
 *
 * 执行类型（canExecute = true，leaseToken 非空）：
 * - ACQUIRED_NEW
 * - RECOVERED_STALE
 * - RETRYABLE_FAILED
 *
 * 非执行类型（canExecute = false，leaseToken 为空）：
 * - ALREADY_COMPLETED
 * - IN_PROGRESS_RECENT
 * - UNKNOWN_STATE_RETRY（失败关闭，不可 ACK）
 */
public interface MqConsumeGuard {

    AcquireResult acquire(String consumer, String eventId);

    /** 租约验证成功 → OK。返回 true 表示成功设置，false 表示租约已失效。 */
    boolean markSuccess(Long logId, String leaseToken);

    /** 租约验证失败 → FAIL。返回 true 表示成功设置，false 表示租约已失效。 */
    boolean markFailure(Long logId, String leaseToken, String error);

    class AcquireResult {
        private final Type type;
        private final Long logId;
        private final String leaseToken;

        public AcquireResult(Type type, Long logId, String leaseToken) {
            this.type = type; this.logId = logId; this.leaseToken = leaseToken;
        }
        public Type type() { return type; }
        public Long logId() { return logId; }
        public String leaseToken() { return leaseToken; }
        public boolean canExecute() {
            return type == Type.ACQUIRED_NEW || type == Type.RECOVERED_STALE || type == Type.RETRYABLE_FAILED;
        }

        public enum Type {
            /** 首次抢占成功，当前实例执行业务 */
            ACQUIRED_NEW,
            /** 已有 OK 记录，直接 ACK */
            ALREADY_COMPLETED,
            /** 最近 PROCESSING，NACK + requeue，不执行业务 */
            IN_PROGRESS_RECENT,
            /** 过期 PROCESSING 被原子接管 */
            RECOVERED_STALE,
            /** FAIL 记录被原子重新抢占 */
            RETRYABLE_FAILED,
            /** 未知状态，失败关闭，抛出异常让消息 NACK */
            UNKNOWN_STATE_RETRY,
        }
    }
}
