package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private Long id;
    private Long userId;           // 卖家ID
    private String title;          // 商品标题
    private String description;    // 商品描述
    private BigDecimal price;      // 价格
    private String images;         // 图片URL，多个用逗号分隔
    private Long categoryId;       // 分类ID
    private String status;         // on_sale-在售, sold-已售, off_shelf-下架
    private Integer viewCount = 0;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")// 浏览量
    private LocalDateTime createTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}