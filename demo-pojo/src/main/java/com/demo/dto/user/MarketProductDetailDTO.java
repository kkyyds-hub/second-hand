package com.demo.dto.user;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MarketProductDetailDTO {
    private Long productId;
    private String title;
    private String description;
    private BigDecimal price;
    private String category;
    private List<String> imageUrls;
    private Long ownerId;
    private LocalDateTime createTime;
}
