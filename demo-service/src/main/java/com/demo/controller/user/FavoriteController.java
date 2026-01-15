package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.base.PageQueryDTO;
import com.demo.result.PageResult;
import com.demo.dto.favorite.FavoriteActionResponse;
import com.demo.dto.favorite.FavoriteItemDTO;
import com.demo.result.Result;
import com.demo.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

@RestController
@RequestMapping("/user/favorites")
@RequiredArgsConstructor
@Validated
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{productId}")
    public Result<FavoriteActionResponse> favorite(@PathVariable @Min(1) Long productId) {
        Long userId = BaseContext.getCurrentId();
        FavoriteActionResponse resp = favoriteService.favorite(userId, productId);
        return Result.success(resp);
    }

    @DeleteMapping("/{productId}")
    public Result<FavoriteActionResponse> unfavorite(@PathVariable @Min(1) Long productId) {
        Long userId = BaseContext.getCurrentId();
        FavoriteActionResponse resp = favoriteService.unfavorite(userId, productId);
        return Result.success(resp);
    }

    @GetMapping
    public Result<PageResult<FavoriteItemDTO>> pageMyFavorites(PageQueryDTO q) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(favoriteService.pageMyFavorites(userId, q));
    }

    @GetMapping("/{productId}/status")
    public Result<Boolean> isFavorited(@PathVariable @Min(1) Long productId) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(favoriteService.isFavorited(userId, productId));
    }

    // 可选 batch（建议用于“列表页/瀑布流”，避免 N+1）
    // @PostMapping("/status/batch")
    // public Result<Map<Long, Boolean>> batchStatus(@RequestBody FavoriteBatchStatusRequest req) { ... }
}
