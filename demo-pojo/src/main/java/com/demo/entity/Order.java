package com.demo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应 orders 表
 */
@Data
public class Order {

    private Long id;                  // 主键ID
    private String orderNo;           // 订单号（业务展示用）

    private Long buyerId;             // 买家ID
    private Long sellerId;            // 卖家ID
    private Long productId;           // 商品ID（当前订单关联的商品）

    private Integer quantity;         // 购买数量
    private BigDecimal dealPrice;     // 成交单价
    private BigDecimal totalAmount;   // 订单总金额 = dealPrice * quantity

    /**
     * 订单状态：
     * pending    - 待付款
     * paid       - 已付款待发货
     * shipped    - 已发货待收货
     * completed  - 已完成
     * cancelled  - 已取消
     *
     * 建议：数据库中直接存这些小写字符串，
     * 业务层通过 OrderStatus 枚举来使用。
     */
    private String status;

    private String shippingAddress;   // 收货地址快照

    private LocalDateTime createTime;   // 下单时间
    private LocalDateTime payTime;      // 支付时间
    private LocalDateTime completeTime; // 完成时间（确认收货等）
}
