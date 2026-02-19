package com.demo.dto.mq;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Day14 - MQ 事件统一信封
 */
@Data
public class EventMessage<T> {
    /** 全局唯一事件 ID（用于幂等/追踪） */
    private String eventId;

    /** 事件类型（例如 ORDER_CREATED / ORDER_PAID） */
    private String eventType;

    /** 实际路由键（用于 RabbitMQ 路由） */
    private String routingKey;

    /** 业务主键（例如 orderId） */
    private Long bizId;

    /** 事件发生时间 */
    private LocalDateTime occurredAt;

    /** 事件载荷（不同事件对应不同结构） */
    private T payload;

    /** 事件版本号（便于兼容升级） */
    private Integer version = 1;
}
