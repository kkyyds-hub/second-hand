package com.demo.dto.user;

import lombok.Data;

@Data
public class SellerSummaryDTO {
    private Long totalProducts;        // 商品总数
    private Long underReviewProducts;  // 审核中
    private Long onSaleProducts;       // 在售
    private Long offShelfProducts;     // 下架
    private Long soldProducts;         // 已售
    
    private Long totalOrders;          // 订单总数
    private Long pendingOrders;        // 待付款
    private Long paidOrders;           // 已付款待发货
    private Long shippedOrders;        // 已发货待收货
    private Long completedOrders;      // 已完成
    private Long cancelledOrders;      // 已取消
}