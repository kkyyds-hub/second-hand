package com.demo.dto.user;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDetailDTO {

    private Long productId;               // 商品ID
    private String title;                 // 商品标题
    private String description;           // 商品描述
    private BigDecimal price;             // 价格
    private List<String> imageUrls;       // 图片URL列表

    private String status;                // 商品状态：UNDER_REVIEW / ON_SHELF / SOLD ...
    private String category;              // 分类名称或编码

    private String reviewRemark;          // 审核备注（如驳回原因，非必填）

    private LocalDateTime createTime;     // 创建时间
    private LocalDateTime updateTime;     // 最近更新时间
    private LocalDateTime submitTime;     // 最近提交审核时间
}
