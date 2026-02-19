package com.demo.service.port.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.entity.Favorite;
import com.demo.mapper.FavoriteMapper;
import com.demo.service.port.FavoriteReadPort;
import org.springframework.stereotype.Component;

/**
 * 收藏只读端口数据库实现。
 */
@Component
public class FavoriteReadPortDbImpl implements FavoriteReadPort {

    private final FavoriteMapper favoriteMapper;

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public FavoriteReadPortDbImpl(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    /**
     * 判断用户是否已收藏指定商品。
     */
    @Override
    public boolean isFavorited(Long userId, Long productId) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .eq(Favorite::getIsDeleted, 0));
        return cnt != null && cnt > 0;
    }

    /**
     * 统计指定商品的收藏总数。
     */
    @Override
    public long countByProductId(Long productId) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getProductId, productId)
                .eq(Favorite::getIsDeleted, 0));
        return cnt == null ? 0L : cnt;
    }
}
