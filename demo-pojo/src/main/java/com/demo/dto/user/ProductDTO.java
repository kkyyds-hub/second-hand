package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDTO {

    private Long productId;       // 商品ID
    private String productName;   // 商品名称
    private String category;      // 商品类别
    private String status;        // 商品审核状态
    private LocalDateTime submitTime; // 提交时间
    private BigDecimal price;         // 商品价格
    private String description;   // 商品描述

}