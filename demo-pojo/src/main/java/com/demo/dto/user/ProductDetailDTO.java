package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情 DTO（卖家侧）。
 */
@Data
public class ProductDetailDTO {

    /** 商品 ID。 */
    private Long productId;
    /** 商品标题。 */
    private String title;
    /** 商品描述。 */
    private String description;
    /** 商品价格。 */
    private BigDecimal price;
    /** 商品图片 URL 列表。 */
    private List<String> imageUrls;

    /** 商品状态。 */
    private String status;
    /** 商品分类。 */
    private String category;
    /** 审核备注（如驳回原因）。 */
    private String reviewRemark;

    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 最近更新时间。 */
    private LocalDateTime updateTime;
    /** 最近提交审核时间。 */
    private LocalDateTime submitTime;
}
