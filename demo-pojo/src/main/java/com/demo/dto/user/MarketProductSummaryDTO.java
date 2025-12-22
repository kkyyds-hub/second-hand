package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MarketProductSummaryDTO {
    private Long productId;
    private String title;
    private BigDecimal price;
    private String category;
    private String thumbnail;
    private Long ownerId;
    private LocalDateTime createTime;
}
