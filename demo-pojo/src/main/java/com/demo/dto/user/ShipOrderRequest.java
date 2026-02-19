package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Day13 - 卖家发货请求 DTO
 */
@Data
public class ShipOrderRequest {

    /**
     * 物流公司/快递公司
     */
    @NotBlank(message = "快递公司不能为空")
    @Size(min = 2, max = 50, message = "快递公司长度需在2~50字符")
    @Pattern(
            regexp = "^[\\p{L}0-9 _.-]{2,50}$",
            message = "快递公司格式不正确"
    )
    /** 字段：shippingCompany。 */
    private String shippingCompany;

    /**
     * 运单号
     */
    @NotBlank(message = "运单号不能为空")
    @Size(min = 6, max = 50, message = "运单号长度需在6~50字符")
    @Pattern(
            regexp = "^[A-Za-z0-9-]{6,50}$",
            message = "运单号格式不正确"
    )
    /** 字段：trackingNo。 */
    private String trackingNo;

    /**
     * 发货备注（可选）
     */
    @Size(max = 200, message = "备注长度不能超过200字符")
    private String remark;
}
