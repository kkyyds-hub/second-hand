package com.demo.vo.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图对象
 *
 * Day5 关键字段：
 * - shippingCompany / trackingNo / shippingRemark
 * - shipTime（建议新增 orders.ship_time）
 * - completeTime（orders.complete_time，确认收货即完成）
 */
@Data
public class OrderDetail {

    // ========= 基础信息 =========
    private Long orderId;              // 订单ID
    private String orderNo;            // 订单号
    private String status;             // 订单状态（pending/paid/shipped/completed/cancelled 等）

    private BigDecimal totalAmount;    // 订单总金额
    private Integer quantity;          // 商品总数量
    private BigDecimal dealPrice;      // 成交单价/成交价（按你订单设计）

    private LocalDateTime createTime;  // 下单时间
    private LocalDateTime payTime;     // 支付时间

    private LocalDateTime shipTime;      // 发货时间（建议新增 orders.ship_time）
    private LocalDateTime completeTime;  // 完成时间（确认收货/订单完成，对应 orders.complete_time）

    private LocalDateTime updateTime;  // 最后更新时间（可选）
    private String shippingAddress;    // 收货地址快照

    // ========= 商品信息 =========
    private Long productId;
    private String productTitle;
    private String productThumbnail;

    /**
     * 商品图片列表（注意：当前用 String 存储）
     * - 如果 DB 是逗号分隔：前端自行 split
     * - 如果 DB 是 JSON 字符串：前端 JSON.parse
     * 建议后续统一为 List<String>，但 Day5 不强制动这一块
     */
    private String productImages;

    // ========= 角色信息 =========
    private Long buyerId;
    private String buyerNickname;

    private Long sellerId;
    private String sellerNickname;

    // ========= 物流信息 =========
    private String shippingCompany;    // 物流公司
    private String trackingNo;         // 运单号
    private String shippingRemark;     // 发货备注（可选）

    // ========= 物流轨迹（可选，非 Day5 必交付） =========
    private List<LogisticsTraceItemVO> logisticsTrace;

    // ========= 沟通 / 评价信息（可选，非 Day5 必交付） =========
    private Long chatSessionId;
    private Boolean allowReview;
    private String buyerReview;
    private String sellerReply;
}
