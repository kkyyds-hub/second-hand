package com.demo.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {
    private Long id;
    private String orderNo;        // 订单号
    private Long buyerId;          // 买家ID
    private Long sellerId;         // 卖家ID
    private BigDecimal totalAmount; // 总金额
    private String status;         // pending-待支付, paid-已支付, shipped-已发货, completed-已完成, cancelled-已取消
    private String shippingAddress; // 收货地址
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime completeTime;
}