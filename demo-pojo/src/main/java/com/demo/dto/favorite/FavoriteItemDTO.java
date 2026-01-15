package com.demo.dto.favorite;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FavoriteItemDTO {
    private Long productId;
    private LocalDateTime favoritedAt;
    private BigDecimal price;
    // 可选：如果你愿意在收藏列表展示商品卡片信息
    private String title;
    private String coverUrl;
    private String status;
}
