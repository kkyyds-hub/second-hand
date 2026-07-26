package com.demo.vo.cart;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车批量结算响应 VO。
 *
 * 采用部分成功模型：成功项进入 orders 并移出购物车，失败项进入 failures 并保留在购物车。
 */
@Data
public class CartCheckoutResponse {

    /** 请求结算的购物车项数量（去重后）。 */
    private Integer requestedCount;

    /** 成功创建订单数量。 */
    private Integer successCount;

    /** 失败数量。 */
    private Integer failureCount;

    /** 成功项列表。 */
    private List<CartCheckoutSuccessVO> orders = new ArrayList<>();

    /** 失败项列表。 */
    private List<CartCheckoutFailureVO> failures = new ArrayList<>();
}
