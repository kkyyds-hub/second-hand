package com.demo.dto.mq;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Day14 - 订单超时事件载荷
 */
@Data
public class OrderTimeoutPayload {
    /** 订单 ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 买家 ID */
    private Long buyerId;
    /** 超时分钟数（用于追踪配置） */
    private Integer pendingMinutes;
    /** 超时触发时间 */
    private LocalDateTime timeoutAt;
    /** 超时原因 */
    private String reason;
}
