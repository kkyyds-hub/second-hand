package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day14 - MQ 消费幂等日志实体。
 *
 * P6-MQ-A-F1：新增 lease_token（租约令牌）、attempt_count（尝试次数）、
 * last_error（最近失败信息）字段，支持原子租约接管和状态防回退。
 *
 * 对应表：mq_consume_log
 */
@Data
public class MqConsumeLog {

    /** 自增主键 */
    private Long id;

    /** 消费者标识（如 OrderPaidConsumer） */
    private String consumer;

    /** 事件唯一 ID（EventMessage.eventId） */
    private String eventId;

    /** 处理状态（PROCESSING / OK / FAIL） */
    private String status;

    /** 租约令牌（每次成功抢占生成新 UUID，用于防止旧租约覆盖新状态） */
    private String leaseToken;

    /** 尝试次数（每次成功抢占 +1） */
    private Integer attemptCount;

    /** 最近错误信息（仅在 markFailure 时写入） */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
