package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.*;

@Data
public class CreateOrderRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /**
     * Day13 要求：收货地址最小长度 5，最大长度 200（文档 2.2）
     */
    @NotBlank(message = "收货地址不能为空")
    @Size(min = 5, max = 200, message = "收货地址长度需在 5~200 字符")
    private String shippingAddress;

    /**
     * 二手商品默认只能买 1 件；数量固定为 1，避免引入库存复杂度
     */
    @Min(value = 1, message = "购买数量必须为1")
    @Max(value = 1, message = "购买数量必须为1")
    private Integer quantity = 1;
}