package com.demo.dto.admin;

import java.util.Arrays;

/**
 * 管理员可以写入的订单风险标记。
 */
public enum OrderFlagType {
    PAYMENT_RISK,
    PRICE_ANOMALY,
    DELIVERY_RISK,
    AFTERSALE_RISK,
    ACCOUNT_RISK,
    MANUAL_REVIEW;

    public static OrderFlagType fromRequestType(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的订单标记类型"));
    }
}
