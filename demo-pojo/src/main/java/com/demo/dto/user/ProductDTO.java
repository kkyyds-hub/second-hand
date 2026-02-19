package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表项 DTO（管理端/卖家端通用）。
 */
@Data
public class ProductDTO {

    /** 商品 ID。 */
    private Long productId;
    /** 商品名称。 */
    private String productName;
    /** 商品分类。 */
    private String category;
    /** 商品状态。 */
    private String status;
    /** 提交时间。 */
    private LocalDateTime submitTime;
    /** 商品价格。 */
    private BigDecimal price;
    /** 商品描述。 */
    private String description;
}
