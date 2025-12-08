package com.demo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应 orders 表
 */
@Data
public class Order {

    private Long id;            // 主键ID
    private String orderNo;     // 订单号（业务展示用）

    private Long buyerId;       // 买家ID
    private Long sellerId;      // 卖家ID

    private BigDecimal totalAmount;   // 订单总金额（所有明细合计）

    /**
     * 订单状态（数据库存小写字符串）：
     * pending   - 待付款
     * paid      - 已付款待发货
     * shipped   - 已发货待收货
     * completed - 已完成
     * cancelled - 已取消
     *
     * 业务代码中建议通过 OrderStatus 枚举使用：
     *   - 落库：order.setStatus(OrderStatus.PAID.getDbValue());
     *   - 读库：OrderStatus.fromDbValue(order.getStatus());
     */
    private String status;

    // 收货信息
    private String shippingAddress;   // 收货地址快照
    private String shippingCompany;   // 物流公司（新加）
    private String trackingNo;        // 运单号（新加）
    private String shippingRemark;    // 发货备注（可选，新加）

    // 时间信息
    private LocalDateTime createTime;   // 下单时间
    private LocalDateTime payTime;      // 支付时间
    private LocalDateTime completeTime; // 完成时间（确认收货等）
    private LocalDateTime updateTime;   // 最后更新时间（新加，可选）
}
