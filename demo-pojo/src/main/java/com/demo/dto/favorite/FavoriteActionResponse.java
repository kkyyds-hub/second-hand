package com.demo.dto.favorite;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FavoriteActionResponse {
    private Long productId;
    private Boolean favorited;
}
