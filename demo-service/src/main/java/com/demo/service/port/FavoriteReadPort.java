package com.demo.service.port;

public interface FavoriteReadPort {
    boolean isFavorited(Long userId, Long productId);
    long countByProductId(Long productId);
}
