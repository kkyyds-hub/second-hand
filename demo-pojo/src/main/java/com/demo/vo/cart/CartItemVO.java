package com.demo.vo.cart;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车列表项 VO。
 *
 * 商品标题、封面、价格、卖家、状态均从当前商品与用户数据实时查询，
 * 不在购物车表存储陈旧副本。
 */
@Data
public class CartItemVO {

    /** 购物车项 ID。 */
    private Long cartItemId;

    /** 商品 ID。 */
    private Long productId;

    /** 商品标题。 */
    private String title;

    /** 封面图 URL。 */
    private String coverUrl;

    /** 商品价格。 */
    private BigDecimal price;

    /** 卖家用户 ID。 */
    private Long sellerId;

    /** 卖家昵称。 */
    private String sellerNickname;

    /** 商品当前状态（on_sale/sold/off_shelf/under_review/deleted 等）。 */
    private String productStatus;

    /** 是否可结算。 */
    private Boolean available;

    /** 不可用原因（可用时为 null）。 */
    private String unavailableReason;

    /** 加入购物车时间。 */
    private LocalDateTime createTime;
}
