package com.demo.dto.mq;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Day14 - 订单状态变更事件载荷
 */
@Data
public class OrderStatusChangedPayload {
    /** 订单 ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 变更前状态 */
    private String oldStatus;
    /** 变更后状态 */
    private String newStatus;
    /** 操作人 ID（用户/系统） */
    private Long operatorId;
    /** 变更时间 */
    private LocalDateTime changeTime;
}
