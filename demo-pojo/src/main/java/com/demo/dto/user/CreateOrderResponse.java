package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateOrderResponse {

    private Long orderId;
    private String orderNo;
    private String status;

    private BigDecimal totalAmount;
    private LocalDateTime createTime;
}
