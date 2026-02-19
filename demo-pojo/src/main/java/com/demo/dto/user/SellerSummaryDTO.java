package com.demo.dto.user;

import lombok.Data;

/**
 * 卖家中心统计摘要 DTO。
 */
@Data
public class SellerSummaryDTO {

    /** 商品总数。 */
    private Long totalProducts;
    /** 待审核商品数。 */
    private Long underReviewProducts;
    /** 在售商品数。 */
    private Long onSaleProducts;
    /** 下架商品数。 */
    private Long offShelfProducts;
    /** 已售商品数。 */
    private Long soldProducts;

    /** 订单总数。 */
    private Long totalOrders;
    /** 待支付订单数。 */
    private Long pendingOrders;
    /** 已支付待发货订单数。 */
    private Long paidOrders;
    /** 已发货待收货订单数。 */
    private Long shippedOrders;
    /** 已完成订单数。 */
    private Long completedOrders;
    /** 已取消订单数。 */
    private Long cancelledOrders;
}
