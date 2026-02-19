package com.demo.dto.review;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * ReviewItemDTO 业务组件。
 */
public class ReviewItemDTO {

    /** 主键 ID。 */
    private Long id;

    /** 订单 ID。 */
    private Long orderId;
    /** 商品 ID。 */
    private Long productId;

    /** 字段：rating。 */
    private Integer rating;
    /** 字段：content。 */
    private String content;

    /** 字段：isAnonymous。 */
    private Boolean isAnonymous;
    /** 创建时间。 */
    private LocalDateTime createTime;

    // 展示字段（匿名口径必须稳定）
    private String buyerDisplayName;
    /** 字段：buyerAvatar。 */
    private String buyerAvatar;

    // 商品展示字段（建议给前端少做一次查询）
    private String productTitle;
    /** 字段：productCover。 */
    private String productCover;
}
