package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场商品详情 DTO（买家侧）。
 */
@Data
public class MarketProductDetailDTO {

    /** 商品 ID。 */
    private Long productId;
    /** 商品标题。 */
    private String title;
    /** 商品描述。 */
    private String description;
    /** 商品价格。 */
    private BigDecimal price;
    /** 商品分类。 */
    private String category;
    /** 商品图片 URL 列表。 */
    private List<String> imageUrls;
    /** 卖家用户 ID。 */
    private Long ownerId;
    /** 商品创建时间。 */
    private LocalDateTime createTime;
}
