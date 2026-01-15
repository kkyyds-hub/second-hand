package com.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.favorite.FavoriteActionResponse;
import com.demo.dto.favorite.FavoriteItemDTO;
import com.demo.entity.Favorite;
import com.demo.result.PageResult;
import org.springframework.transaction.annotation.Transactional;

public interface FavoriteService extends IService<Favorite> {


    @Transactional
    FavoriteActionResponse favorite(Long userId, Long productId);

    @Transactional
    FavoriteActionResponse unfavorite(Long userId, Long productId);

    PageResult<FavoriteItemDTO> pageMyFavorites(Long userId, PageQueryDTO pageQuery);

    boolean isFavorited(Long userId, Long productId);
}
