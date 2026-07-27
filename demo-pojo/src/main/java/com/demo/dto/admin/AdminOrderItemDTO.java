package com.demo.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端订单详情中的订单项。
 */
@Data
public class AdminOrderItemDTO {
    private Long productId;
    private String productTitle;
    private String productThumbnail;
    private String productStatus;
    private BigDecimal price;
    private Integer quantity;
}
