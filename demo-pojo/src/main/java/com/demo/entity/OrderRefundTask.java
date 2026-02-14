package com.demo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款任务实体
 *
 * 对应表：order_refund_task
 * 作用：承接“超时取消后退款”等补偿动作，确保退款逻辑可重试、可幂等。
 */
@Data
public class OrderRefundTask {

    /** 主键ID */
    private Long id;

    /** 关联订单ID */
    private Long orderId;

    /**
     * 退款类型：
     * ship_timeout - 发货超时退款
     * after_sale   - 售后退款（后续可扩展）
     */
    private String refundType;

    /** 退款金额 */
    private BigDecimal amount;

    /**
     * 任务状态：
     * PENDING - 待处理
     * SUCCESS - 已成功
     * FAILED  - 失败待重试
     */
    private String status;

    /** 幂等键（强约束：同一退款动作只能成功一次） */
    private String idempotencyKey;

    /** 当前累计重试次数 */
    private Integer retryCount;

    /** 下次允许重试时间 */
    private LocalDateTime nextRetryTime;

    /** 最近一次失败原因 */
    private String failReason;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
