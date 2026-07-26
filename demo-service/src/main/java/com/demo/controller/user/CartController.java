package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.cart.AddCartItemRequest;
import com.demo.dto.cart.BatchDeleteCartItemsRequest;
import com.demo.dto.cart.CartCheckoutRequest;
import com.demo.result.Result;
import com.demo.service.CartService;
import com.demo.vo.cart.CartCheckoutResponse;
import com.demo.vo.cart.CartCountVO;
import com.demo.vo.cart.CartItemVO;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * 用户购物车接口。
 */
@RestController
@RequestMapping("/user/cart")
@RequiredArgsConstructor
@Validated
@Api(tags = "用户购物车接口")
@Slf4j
public class CartController {

    private final CartService cartService;

    /**
     * 查询当前用户全部购物车项（按加入时间倒序）。
     */
    @GetMapping("/items")
    public Result<List<CartItemVO>> listItems() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(cartService.listItems(userId));
    }

    /**
     * 查询当前用户购物车数量统计。
     */
    @GetMapping("/count")
    public Result<CartCountVO> count() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(cartService.count(userId));
    }

    /**
     * 加入购物车（幂等）。
     */
    @PostMapping("/items")
    public Result<CartItemVO> add(@Valid @RequestBody AddCartItemRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("加入购物车：userId={}, productId={}", userId, request.getProductId());
        return Result.success(cartService.add(userId, request));
    }

    /**
     * 删除当前用户的单个购物车项（幂等）。
     */
    @DeleteMapping("/items/{cartItemId}")
    public Result<String> delete(@PathVariable @Min(value = 1, message = "购物车项 ID 必须大于 0") Long cartItemId) {
        Long userId = BaseContext.getCurrentId();
        cartService.delete(userId, cartItemId);
        return Result.success("删除成功");
    }

    /**
     * 批量删除当前用户购物车项，返回实际删除数量。
     */
    @PostMapping("/items/batch-delete")
    public Result<Integer> batchDelete(@Valid @RequestBody BatchDeleteCartItemsRequest request) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(cartService.batchDelete(userId, request));
    }

    /**
     * 批量结算（部分成功模型）。
     */
    @PostMapping("/checkout")
    public Result<CartCheckoutResponse> checkout(@Valid @RequestBody CartCheckoutRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("购物车批量结算：userId={}, itemCount={}, addressId={}",
                userId, request.getCartItemIds() == null ? 0 : request.getCartItemIds().size(),
                request.getAddressId());
        return Result.success(cartService.checkout(userId, request));
    }
}
