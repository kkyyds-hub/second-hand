package com.demo.enumeration;

import java.util.EnumSet;

public enum OrderStatus {

    PENDING("pending", "待付款"),
    PAID("paid", "已付款待发货"),
    SHIPPED("shipped", "已发货待收货"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    private final String dbValue;
    private final String description;

    OrderStatus(String dbValue, String description) {
        this.dbValue = dbValue;
        this.description = description;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDescription() {
        return description;
    }

    public static OrderStatus fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (OrderStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(dbValue)) {
                return status;
            }
        }
        return null;
    }

    // ====== 状态机口径冻结（后续所有 Service 判断都走这里） ======

    public boolean canPay() {
        return this == PENDING;
    }

    public boolean canCancelByBuyer() {
        return this == PENDING;
    }

    public boolean canShip() {
        return this == PAID;
    }

    public boolean canConfirm() {
        return this == SHIPPED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean isPaidOrLater() {
        return EnumSet.of(PAID, SHIPPED, COMPLETED).contains(this);
    }
}
