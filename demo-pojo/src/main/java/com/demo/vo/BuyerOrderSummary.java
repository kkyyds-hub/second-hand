package com.demo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 买家“我的订单列表”使用的订单摘要视图
 */
@Data
public class BuyerOrderSummary {

    private Long orderId;          // 订单ID
    private String orderNo;        // 订单编号

    private Long productId;        // 商品ID
    private String productTitle;   // 商品标题
    private String productThumbnail; // 商品封面图

    private BigDecimal dealPrice;  // 成交单价
    private Integer quantity;      // 购买数量

    private String sellerNickname; // 卖家昵称

    /**
     * 订单状态（pending/paid/shipped/completed/cancelled）
     * 也可以在序列化时直接返回枚举的 dbValue
     */
    private String status;

    private LocalDateTime createTime; // 下单时间
    private LocalDateTime payTime;    // 支付时间（未支付则为 null）
}
