package com.demo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单项实体。
 */
@Data
public class OrderItem {

    /** 订单项 ID。 */
    private Long id;

    /** 订单 ID。 */
    private Long orderId;
    /** 商品 ID。 */
    private Long productId;

    /** 成交单价。 */
    private BigDecimal price;
    /** 购买数量。 */
    private Integer quantity;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
