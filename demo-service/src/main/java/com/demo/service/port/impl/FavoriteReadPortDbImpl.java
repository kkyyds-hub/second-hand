package com.demo.service.port.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.entity.Favorite;
import com.demo.mapper.FavoriteMapper;
import com.demo.service.port.FavoriteReadPort;
import org.springframework.stereotype.Component;

@Component
public class FavoriteReadPortDbImpl implements FavoriteReadPort {

    private final FavoriteMapper favoriteMapper;

    public FavoriteReadPortDbImpl(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .eq(Favorite::getIsDeleted, 0));
        return cnt != null && cnt > 0;
    }

    @Override
    public long countByProductId(Long productId) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getProductId, productId)
                .eq(Favorite::getIsDeleted, 0));
        return cnt == null ? 0L : cnt;
    }
}
