package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发货超时提醒任务实体。
 *
 * 对应表：order_ship_reminder_task
 * 作用：
 * 1) 记录“支付后 48h 内未发货”的提醒档位任务（H24/H6/H1）
 * 2) 通过状态机 + 重试字段保证任务可恢复、可追踪
 */
@Data
public class OrderShipReminderTask {

    /** 主键 ID */
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 卖家 ID（提醒接收人） */
    private Long sellerId;

    /**
     * 提醒档位：
     * H24 - 距离超时 24 小时
     * H6  - 距离超时 6 小时
     * H1  - 距离超时 1 小时
     */
    private String level;

    /** 发货截止时间（通常 = pay_time + 48h） */
    private LocalDateTime deadlineTime;

    /** 本任务计划执行时间（到点后由 Job 扫描执行） */
    private LocalDateTime remindTime;

    /**
     * 任务状态：
     * PENDING   - 待执行
     * RUNNING   - 已被某轮 Job 抢占处理中
     * SUCCESS   - 已发送成功（或幂等命中视作成功）
     * FAILED    - 发送失败，等待下一次重试
     * CANCELLED - 业务上无需提醒（如已发货/已取消）
     */
    private String status;

    /** 当前累计失败次数（用于退避重试） */
    private Integer retryCount;

    /** 进入 RUNNING 的时间（用于检测“卡住任务”） */
    private LocalDateTime runningAt;

    /** 成功发送时间 */
    private LocalDateTime sentAt;

    /** 实际发送使用的幂等键（clientMsgId） */
    private String clientMsgId;

    /** 最近一次失败原因（截断保存，便于排障） */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
