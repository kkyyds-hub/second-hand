package com.demo.dto.user;

import lombok.Data;

/**
 * 买家取消订单请求（reason 可选）
 */
@Data
public class CancelOrderRequest {
    private String reason;
}
