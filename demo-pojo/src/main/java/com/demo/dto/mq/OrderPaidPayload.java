package com.demo.dto.mq;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day14 - 支付完成事件载荷
 */
@Data
public class OrderPaidPayload {
    /** 订单 ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 买家 ID */
    private Long buyerId;
    /** 支付金额 */
    private BigDecimal payAmount;
    /** 支付方式（如 ALIPAY/WECHAT） */
    private String payMethod;
    /** 支付时间 */
    private LocalDateTime payTime;
}
