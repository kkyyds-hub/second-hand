package com.demo.dto.seller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 卖家订单统计（单行汇总）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerOrderCountDTO {

    /** 订单总数 */
    private Long totalOrders;

    /** 待支付/待处理（按你项目状态机口径） */
    private Long pendingOrders;

    /** 已支付 */
    private Long paidOrders;

    /** 已发货 */
    private Long shippedOrders;

    /** 已完成 */
    private Long completedOrders;

    /** 已取消 */
    private Long cancelledOrders;
}
