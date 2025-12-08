package com.demo.vo.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图对象，对应接口文档中的 OrderDetail
 */
@Data
public class OrderDetail {

    // ========= 基础信息 =========
    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 订单状态（pending/paid/shipped/completed/cancelled 等） */
    private String status;

    /** 订单总金额（totalAmount） */
    private BigDecimal totalAmount;

    /** 商品总数量（quantity） */
    private Integer quantity;

    /** 成交单价/成交价（dealPrice，按你订单设计来，通常是单件成交价） */
    private BigDecimal dealPrice;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 完成时间（收货/确认完成） */
    private LocalDateTime completeTime;
    private LocalDateTime updateTime;

    /** 收货地址快照（shippingAddress） */
    private String shippingAddress;

    // ========= 商品信息 =========
    /** 商品ID */
    private Long productId;

    /** 商品标题 */
    private String productTitle;

    /** 商品缩略图 */
    private String productThumbnail;

    /** 商品图片列表（前端期望的是数组） */
    private String productImages;

    // ========= 角色信息 =========
    /** 买家ID */
    private Long buyerId;

    /** 买家昵称 */
    private String buyerNickname;

    /** 卖家ID */
    private Long sellerId;

    /** 卖家昵称 */
    private String sellerNickname;

    // ========= 物流信息 =========
    /** 物流公司 */
    private String shippingCompany;

    /** 运单号 */
    private String trackingNo;

    /**
     * 物流轨迹列表：logisticsTrace[]
     * 每一条包含节点时间、地点、状态
     */
    private List<LogisticsTraceItemVO> logisticsTrace;

    // ========= 沟通 / 评价信息 =========
    /** 聊天会话ID（用于消息记录） */
    private Long chatSessionId;

    /** 是否可以评价（allowReview） */
    private Boolean allowReview;

    /** 买家评价内容（简单版本，后续可扩展为独立 VO） */
    private String buyerReview;

    /** 卖家回复内容 */
    private String sellerReply;


}
