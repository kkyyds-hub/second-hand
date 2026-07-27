package com.demo.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端订单详情的只读聚合投影。
 */
@Data
public class AdminOrderDetailDTO {
    private Long orderId;
    private String orderNo;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private Long buyerId;
    private String buyerNickname;
    private String buyerMobile;
    private Boolean buyerDeleted;
    private Long sellerId;
    private String sellerNickname;
    private String sellerMobile;
    private Boolean sellerDeleted;
    private String shippingAddress;
    private String shippingCompany;
    private String trackingNo;
    private String shippingRemark;
    private Long afterSaleId;
    private String afterSaleStatus;
    private String afterSaleReason;
    private String afterSalePlatformRemark;
    private List<AdminOrderItemDTO> items;
    private List<AdminOrderFlagDTO> flags;
}
