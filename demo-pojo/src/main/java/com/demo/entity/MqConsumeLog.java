package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day14 - MQ 消费幂等日志实体
 *
 * 对应表：mq_consume_log
 * 作用：记录某个消费者是否已处理过某条事件，防重复消费
 */
@Data
public class MqConsumeLog {

    /** 自增主键 */
    private Long id;

    /** 消费者标识（如 OrderPaidConsumer） */
    private String consumer;

    /** 事件唯一 ID（EventMessage.eventId） */
    private String eventId;

    /** 处理状态（OK/FAIL） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
