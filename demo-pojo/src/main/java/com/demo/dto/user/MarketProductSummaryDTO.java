package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 市场商品列表项 DTO（买家侧）。
 */
@Data
public class MarketProductSummaryDTO {

    /** 商品 ID。 */
    private Long productId;
    /** 商品标题。 */
    private String title;
    /** 商品价格。 */
    private BigDecimal price;
    /** 商品分类。 */
    private String category;
    /** 商品缩略图 URL。 */
    private String thumbnail;
    /** 卖家用户 ID。 */
    private Long ownerId;
    /** 商品创建时间。 */
    private LocalDateTime createTime;
}
