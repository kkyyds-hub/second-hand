package com.demo.vo.cart;

import lombok.Data;

/**
 * 购物车数量统计 VO。
 */
@Data
public class CartCountVO {

    /** 购物车总项数。 */
    private Integer total;

    /** 可结算项数。 */
    private Integer available;
}
