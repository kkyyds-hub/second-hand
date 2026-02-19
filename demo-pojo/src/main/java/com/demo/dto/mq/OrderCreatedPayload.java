package com.demo.dto.mq;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day14 - 订单创建事件载荷
 */
@Data
public class OrderCreatedPayload {
    /** 订单 ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 买家 ID */
    private Long buyerId;
    /** 卖家 ID */
    private Long sellerId;
    /** 商品 ID */
    private Long productId;
    /** 购买数量 */
    private Integer quantity;
    /** 单价 */
    private BigDecimal price;
    /** 订单总金额 */
    private BigDecimal totalAmount;
    /** 下单时间 */
    private LocalDateTime createTime;
}
