package com.demo.dto.cart;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 加入购物车请求 DTO。
 */
@Data
public class AddCartItemRequest {

    /** 商品 ID。 */
    @NotNull(message = "商品 ID 不能为空")
    @Min(value = 1, message = "商品 ID 必须大于 0")
    private Long productId;
}
