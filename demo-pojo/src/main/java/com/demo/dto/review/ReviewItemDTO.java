package com.demo.dto.review;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewItemDTO {

    private Long id;

    private Long orderId;
    private Long productId;

    private Integer rating;
    private String content;

    private Boolean isAnonymous;
    private LocalDateTime createTime;

    // 展示字段（匿名口径必须稳定）
    private String buyerDisplayName;
    private String buyerAvatar;

    // 商品展示字段（建议给前端少做一次查询）
    private String productTitle;
    private String productCover;
}
