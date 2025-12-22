package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CreateOrderRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /**
     * Day3 先用地址快照字符串（等 Address 模块完善后可改为 addressId -> snapshot）
     */
    @NotBlank(message = "收货地址不能为空")
    private String shippingAddress;

    /**
     * 二手商品默认只能买 1 件；先把数量模型冻结为 1，避免引入库存复杂度
     */
    @Min(value = 1, message = "购买数量必须为1")
    @Max(value = 1, message = "购买数量必须为1")
    private Integer quantity = 1;
}
