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
     * 建议值：buyer_cancel / wrong_info / no_need 等
     */
    @Size(max = 100, message = "取消原因长度不能超过100")
    private String reason;
}
