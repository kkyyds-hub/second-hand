package com.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.favorite.FavoriteActionResponse;
import com.demo.dto.favorite.FavoriteItemDTO;
import com.demo.entity.Favorite;
import com.demo.result.PageResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏领域服务接口。
 * 提供收藏、新增取消、分页查询与状态判断能力。
 */
public interface FavoriteService extends IService<Favorite> {

    /**
     * 将商品加入用户收藏。
     */
    @Transactional
    FavoriteActionResponse favorite(Long userId, Long productId);

    /**
     * 将商品从用户收藏中移除。
     */
    @Transactional
    FavoriteActionResponse unfavorite(Long userId, Long productId);

    /**
     * 分页查询当前用户收藏列表。
     */
    PageResult<FavoriteItemDTO> pageMyFavorites(Long userId, PageQueryDTO pageQuery);

    /**
     * 判断用户是否已收藏指定商品。
     */
    boolean isFavorited(Long userId, Long productId);
}
