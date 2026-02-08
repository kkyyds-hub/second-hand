package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day14 - 事务性外箱实体
 *
 * 对应表：message_outbox
 * 作用：在本地事务中持久化“待发送的 MQ 事件”
 */
@Data
public class MessageOutbox {

    /** 自增主键 */
    private Long id;

    /** 事件唯一ID（幂等/追踪） */
    private String eventId;

    /** 事件类型（ORDER_CREATED/ORDER_PAID/...） */
    private String eventType;

    /** 交换机名称 */
    private String exchangeName;

    /** 路由键 */
    private String routingKey;

    /** 业务主键（如 orderId） */
    private Long bizId;

    /** 事件载荷 JSON */
    private String payloadJson;

    /**
     * 状态：
     * NEW  - 待发送
     * SENT - 已发送
     * FAIL - 发送失败
     */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次可重试时间 */
    private LocalDateTime nextRetryTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
