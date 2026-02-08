package com.demo.dto.mq;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day14 - 订单创建事件载荷
 */
@Data
public class OrderCreatedPayload {
    /** 订单ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 买家ID */
    private Long buyerId;
    /** 卖家ID */
    private Long sellerId;
    /** 商品ID */
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
