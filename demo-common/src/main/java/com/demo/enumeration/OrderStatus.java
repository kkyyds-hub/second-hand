package com.demo.enumeration;

import java.util.EnumSet;

/**
 * 订单状态枚举。
 * 统一维护数据库值映射和状态机判断口径。
 */
public enum OrderStatus {

    /** 待支付。 */
    PENDING("pending", "待付款"),
    /** 已支付待发货。 */
    PAID("paid", "已付款待发货"),
    /** 已发货待收货。 */
    SHIPPED("shipped", "已发货待收货"),
    /** 已完成。 */
    COMPLETED("completed", "已完成"),
    /** 已取消。 */
    CANCELLED("cancelled", "已取消");

    /** 数据库存储值。 */
    private final String dbValue;
    /** 业务语义说明。 */
    private final String description;

    OrderStatus(String dbValue, String description) {
        this.dbValue = dbValue;
        this.description = description;
    }

    /**
     * 获取数据库存储值。
     */
    public String getDbValue() {
        return dbValue;
    }

    /**
     * 获取状态描述。
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据数据库值解析订单状态。
     */
    public static OrderStatus fromDbValue(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(dbValue)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 当前状态是否允许支付。
     */
    public boolean canPay() {
        return this == PENDING;
    }

    /**
     * 当前状态是否允许买家取消。
     */
    public boolean canCancelByBuyer() {
        return this == PENDING;
    }

    /**
     * 当前状态是否允许卖家发货。
     */
    public boolean canShip() {
        return this == PAID;
    }

    /**
     * 当前状态是否允许买家确认收货。
     */
    public boolean canConfirm() {
        return this == SHIPPED;
    }

    /**
     * 是否终态（已完成或已取消）。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * 是否处于已支付及后续状态。
     */
    public boolean isPaidOrLater() {
        return EnumSet.of(PAID, SHIPPED, COMPLETED).contains(this);
    }
}
