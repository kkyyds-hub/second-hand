package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 卖家发货请求
 */
@Data
public class ShipOrderRequest {

    @NotBlank(message = "快递公司不能为空")
    private String shippingCompany;

    @NotBlank(message = "运单号不能为空")
    private String trackingNo;

    // 备注，可以先不落库
    private String remark;
}
