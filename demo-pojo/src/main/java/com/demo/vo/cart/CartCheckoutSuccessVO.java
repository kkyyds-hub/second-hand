package com.demo.vo.cart;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 批量结算成功项 VO。
 */
@Data
public class CartCheckoutSuccessVO {

    /** 购物车项 ID。 */
    private Long cartItemId;

    /** 商品 ID。 */
    private Long productId;

    /** 订单 ID。 */
    private Long orderId;

    /** 订单编号。 */
    private String orderNo;

    /** 订单状态。 */
    private String status;

    /** 订单总金额。 */
    private BigDecimal totalAmount;
}
