package com.demo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderItem {

    private Long id;

    private Long orderId;
    private Long productId;

    private BigDecimal price;
    private Integer quantity;

    private LocalDateTime createTime;
}
