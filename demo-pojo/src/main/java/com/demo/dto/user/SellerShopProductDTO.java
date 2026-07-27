package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卖家小店商品摘要 DTO。
 */
@Data
public class SellerShopProductDTO {

    /** 商品 ID。 */
    private Long productId;
    /** 商品标题。 */
    private String title;
    /** 商品封面图 URL。 */
    private String coverUrl;
    /** 商品价格。 */
    private BigDecimal price;
    /** 商品分类名称。 */
    private String categoryName;
    /** 商品状态（on_sale / sold）。 */
    private String status;
    /** 商品创建时间。 */
    private LocalDateTime createTime;
}
