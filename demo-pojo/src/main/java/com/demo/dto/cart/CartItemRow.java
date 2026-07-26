package com.demo.dto.cart;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车列表联表查询的原始行投影（内部使用）。
 *
 * 保留商品 is_deleted 与 owner_id 等原始字段，
 * 由 CartService 统一计算 available 与 unavailableReason，
 * 避免把状态判定逻辑分散到 SQL。
 */
@Data
public class CartItemRow {

    /** 购物车项 ID。 */
    private Long cartItemId;

    /** 商品 ID。 */
    private Long productId;

    /** 商品标题。 */
    private String title;

    /** 商品图片（逗号分隔）。 */
    private String images;

    /** 商品价格。 */
    private BigDecimal price;

    /** 卖家用户 ID（商品 owner_id）。 */
    private Long sellerId;

    /** 卖家昵称。 */
    private String sellerNickname;

    /** 商品状态（商品被删除时为 null）。 */
    private String productStatus;

    /** 商品逻辑删除标记（商品行不存在时为 null）。 */
    private Integer productDeleted;

    /** 加入购物车时间。 */
    private LocalDateTime createTime;
}
