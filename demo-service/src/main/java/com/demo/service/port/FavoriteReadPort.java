package com.demo.service.port;

/**
 * 收藏聚合只读端口。
 */
public interface FavoriteReadPort {

    /**
     * 判断用户是否已收藏指定商品。
     */
    boolean isFavorited(Long userId, Long productId);

    /**
     * 统计某个商品的收藏数量。
     */
    long countByProductId(Long productId);
}
