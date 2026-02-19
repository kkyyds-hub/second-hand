package com.demo.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day13 Step7 - 后台订单分页列表 DTO
 */
@Data
public class AdminOrderDTO {

    /** 订单 ID。 */
    private Long orderId;
    /** 字段：orderNo。 */
    private String orderNo;

    /** 买家用户 ID。 */
    private Long buyerId;
    /** 卖家用户 ID。 */
    private Long sellerId;

    /** 字段：totalAmount。 */
    private BigDecimal totalAmount;
    /** 状态。 */
    private String status;

    /** 字段：shippingCompany。 */
    private String shippingCompany;
    /** 字段：trackingNo。 */
    private String trackingNo;

    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 时间字段。 */
    private LocalDateTime payTime;
    /** 时间字段。 */
    private LocalDateTime shipTime;
    /** 时间字段。 */
    private LocalDateTime completeTime;
    /** 时间字段。 */
    private LocalDateTime cancelTime;
    /** 字段：cancelReason。 */
    private String cancelReason;
}
