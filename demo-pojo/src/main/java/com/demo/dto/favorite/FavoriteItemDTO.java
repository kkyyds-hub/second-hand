package com.demo.dto.favorite;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏列表项 DTO。
 */
@Data
public class FavoriteItemDTO {
    /** 商品 ID。 */
    private Long productId;
    /** 收藏时间。 */
    private LocalDateTime favoritedAt;
    /** 商品价格。 */
    private BigDecimal price;
    // 可选：如果你愿意在收藏列表展示商品卡片信息
    /** 商品标题。 */
    private String title;
    /** 封面图 URL。 */
    private String coverUrl;
    /** 商品状态。 */
    private String status;
}
