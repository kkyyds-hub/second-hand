package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.*;

@Data
/**
 * CreateOrderRequest 业务组件。
 */
public class CreateOrderRequest {

    @NotNull(message = "商品 ID 不能为空")
    @Min(value = 1, message = "商品 ID 必须大于 0")
    /** 商品 ID。 */
    private Long productId;

    @NotNull(message = "收货地址不能为空")
    @Min(value = 1, message = "收货地址 ID 必须大于 0")
    /** 当前用户已保存的收货地址 ID。 */
    private Long addressId;
}
