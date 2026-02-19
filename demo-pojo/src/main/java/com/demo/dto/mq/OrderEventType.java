package com.demo.dto.mq;

/**
 * Day14 - 订单域事件类型枚举
 */
public enum OrderEventType {
    /** 订单创建 */
    ORDER_CREATED("ORDER_CREATED"),
    /** 支付完成 */
    ORDER_PAID("ORDER_PAID"),
    /** 订单状态变更 */
    ORDER_STATUS_CHANGED("ORDER_STATUS_CHANGED"),
    /** 订单超时 */
    ORDER_TIMEOUT("ORDER_TIMEOUT");

    /** 事件编码 */
    private final String code;

    OrderEventType(String code) {
        this.code = code;
    }

    /** 获取事件编码 */
    /**
     * 获取事件编码。
     */
    public String getCode() {
        return code;
    }
}
