package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 买家取消订单请求
 */
@Data
public class CancelOrderRequest {

    /**
     * 取消原因（可选）
     * 建议传：buyer_cancel / wrong_info / no_need 等短码
     */
    @Size(max = 64, message = "取消原因长度不能超过64")
    private String reason;
}
