package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建订单响应 DTO。
 */
@Data
public class CreateOrderResponse {

    /** 订单 ID。 */
    private Long orderId;
    /** 订单编号。 */
    private String orderNo;
    /** 订单状态。 */
    private String status;

    /** 订单总金额。 */
    private BigDecimal totalAmount;
    /** 下单时间。 */
    private LocalDateTime createTime;
}
