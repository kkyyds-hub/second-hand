package com.demo.dto.favorite;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 收藏动作响应 DTO。
 */
@Data
@AllArgsConstructor
public class FavoriteActionResponse {

    /** 操作目标商品 ID。 */
    private Long productId;

    /** 操作后收藏状态。 */
    private Boolean favorited;
}
