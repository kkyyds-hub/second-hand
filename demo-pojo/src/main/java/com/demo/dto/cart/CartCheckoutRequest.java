package com.demo.dto.cart;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 购物车批量结算请求 DTO。
 */
@Data
public class CartCheckoutRequest {

    /** 参与结算的购物车项 ID 列表（去重后最多 20 项）。 */
    @NotEmpty(message = "购物车项 ID 列表不能为空")
    @Size(max = 20, message = "单次最多结算 20 项")
    private List<Long> cartItemIds;

    /** 收货地址 ID。 */
    @NotNull(message = "收货地址不能为空")
    @Min(value = 1, message = "收货地址 ID 必须大于 0")
    private Long addressId;
}
