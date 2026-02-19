package com.demo.vo.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卖家“我的订单列表/卖出记录”
 *
 * Day5 补齐：
 * - shipTime：发货时间（建议新增 orders.ship_time）
 * - completeTime：确认收货/订单完成时间（orders.complete_time）
 */
@Data
public class SellerOrderSummary {

    private Long orderId;            // 订单 ID
    private String orderNo;          // 订单编号

    private Long productId;          // 商品 ID
    private String productTitle;     // 商品标题
    private String productThumbnail; // 商品封面图

    private BigDecimal dealPrice;    // 成交单价
    private Integer quantity;        // 购买数量

    private String buyerNickname;    // 买家昵称

    /**
     * 订单状态（pending/paid/shipped/completed/cancelled）
     */
    private String status;

    private String shippingCompany;  // 物流公司（未发货可为 null）
    private String trackingNo;       // 运单号（未发货可为 null）

    private LocalDateTime shipTime;     // 发货时间（未发货可为 null）
    private LocalDateTime completeTime; // 完成/确认收货时间（未完成可为 null）

    private LocalDateTime createTime; // 下单时间
    private LocalDateTime payTime;    // 支付时间（未支付则为 null）
}
