package com.demo.vo.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卖家“我的订单列表/卖出记录”
 */
@Data
public class SellerOrderSummary {

    private Long orderId;           // 订单ID
    private String orderNo;         // 订单编号

    private Long productId;         // 商品ID
    private String productTitle;    // 商品标题
    private String productThumbnail;// 商品封面图

    private BigDecimal dealPrice;   // 成交单价
    private Integer quantity;       // 购买数量

    private String buyerNickname;   // 买家昵称

    /**
     * 订单状态（pending/paid/shipped/completed/cancelled）
     */
    private String status;

    /**
     * 物流公司（可选字段，未发货时可能为 null）
     */
    private String shippingCompany;

    /**
     * 物流单号（可选字段，未发货时可能为 null）
     */
    private String trackingNo;

    private LocalDateTime createTime; // 下单时间
    private LocalDateTime payTime;    // 支付时间（未支付则为 null）
}
