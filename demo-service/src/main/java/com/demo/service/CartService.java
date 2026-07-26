package com.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.dto.cart.AddCartItemRequest;
import com.demo.dto.cart.BatchDeleteCartItemsRequest;
import com.demo.dto.cart.CartCheckoutRequest;
import com.demo.entity.CartItem;
import com.demo.vo.cart.CartCheckoutResponse;
import com.demo.vo.cart.CartCountVO;
import com.demo.vo.cart.CartItemVO;

import java.util.List;

/**
 * 购物车领域服务接口。
 *
 * 二手商品口径：每项数量固定为 1，不占用商品，仅在正式创建订单时扣减。
 */
public interface CartService extends IService<CartItem> {

    /**
     * 查询当前用户全部购物车项（按加入时间倒序），实时计算可用性。
     */
    List<CartItemVO> listItems(Long userId);

    /**
     * 统计当前用户购物车总数与可结算数。
     */
    CartCountVO count(Long userId);

    /**
     * 将商品加入购物车（幂等）。
     */
    CartItemVO add(Long userId, AddCartItemRequest request);

    /**
     * 删除当前用户的单个购物车项（幂等，不泄漏归属）。
     */
    void delete(Long userId, Long cartItemId);

    /**
     * 批量删除当前用户购物车项，返回实际删除数量。
     */
    int batchDelete(Long userId, BatchDeleteCartItemsRequest request);

    /**
     * 批量结算：逐项复用 OrderService.createOrder，部分成功模型。
     */
    CartCheckoutResponse checkout(Long userId, CartCheckoutRequest request);
}
