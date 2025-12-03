package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {

    private Long id;                  // 商品ID
    private String title;             // 商品标题
    private String description;       // 商品描述
    private BigDecimal price;         // 商品价格
    private String images;            // 商品图片URL，多个用逗号分隔
    private String category;          // 商品类别
    private String status;            // 商品状态 (例如: "on_sale"、"sold"、"off_shelf")
    private Integer viewCount = 0;    // 浏览量，默认为0
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime; // 商品创建时间
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime; // 商品更新时间
    private String reason;
    private Long ownerId;
}
