package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发货超时任务实体
 *
 * 对应表：order_ship_timeout_task
 * 作用：记录“支付后 48h 未发货”的待处理任务，支持重试与状态流转。
 */
@Data
public class OrderShipTimeoutTask {

    /** 主键ID */
    private Long id;

    /** 关联订单ID（唯一） */
    private Long orderId;

    /** 超时截止时间（通常 = pay_time + 48h） */
    private LocalDateTime deadlineTime;

    /**
     * 任务状态：
     * PENDING   - 待处理
     * DONE      - 已完成（成功执行超时取消逻辑）
     * CANCELLED - 已取消（例如订单已发货/已完成，无需继续处理）
     */
    private String status;

    /** 当前累计重试次数 */
    private Integer retryCount;

    /** 下次允许重试时间（为空表示可立即重试） */
    private LocalDateTime nextRetryTime;

    /** 最近一次处理失败原因（便于排查） */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
