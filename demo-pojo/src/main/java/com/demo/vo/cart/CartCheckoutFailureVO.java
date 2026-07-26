package com.demo.vo.cart;

import lombok.Data;

/**
 * 批量结算失败项 VO。
 */
@Data
public class CartCheckoutFailureVO {

    /** 购物车项 ID。 */
    private Long cartItemId;

    /** 商品 ID。 */
    private Long productId;

    /** 失败原因。 */
    private String reason;
}
