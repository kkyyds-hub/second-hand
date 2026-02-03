package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Day13 Step7 - 订单异常标记请求
 */
@Data
public class OrderFlagRequest {

    /**
     * 标记类型：suspicious / refund_risk / other
     */
    @NotBlank(message = "标记类型不能为空")
    @Size(max = 32, message = "标记类型长度不能超过32")
    private String type;

    /**
     * 备注说明
     */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
