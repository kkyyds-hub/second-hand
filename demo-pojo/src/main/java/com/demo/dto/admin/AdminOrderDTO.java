package com.demo.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day13 Step7 - 后台订单分页列表 DTO
 */
@Data
public class AdminOrderDTO {

    private Long orderId;
    private String orderNo;

    private Long buyerId;
    private Long sellerId;

    private BigDecimal totalAmount;
    private String status;

    private String shippingCompany;
    private String trackingNo;

    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
}
