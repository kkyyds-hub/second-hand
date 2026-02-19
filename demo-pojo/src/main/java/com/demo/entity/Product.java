package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体。
 */
@Data
public class Product {

    /** 商品 ID。 */
    private Long id;
    /** 商品标题。 */
    private String title;
    /** 商品描述。 */
    private String description;
    /** 商品价格。 */
    private BigDecimal price;
    /** 商品图片 URL（逗号分隔）。 */
    private String images;
    /** 商品分类。 */
    private String category;
    /** 商品状态（如 on_sale/sold/off_shelf/under_review）。 */
    private String status;
    /** 浏览量。 */
    private Integer viewCount = 0;

    /** 创建时间。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 更新时间。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 审核/状态变更备注。 */
    private String reason;
    /** 商品所有者用户 ID。 */
    private Long ownerId;
    /** 逻辑删除标记（0=未删除，1=已删除）。 */
    private int isDeleted;
}
