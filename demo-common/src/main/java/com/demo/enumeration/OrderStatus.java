package com.demo.enumeration;

public enum OrderStatus {

    PENDING("pending", "待付款"),
    PAID("paid", "已付款待发货"),
    SHIPPED("shipped", "已发货待收货"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    /**
     * 存在数据库中的值
     */
    private final String dbValue;

    /**
     * 中文描述（用于前端展示、日志、报表等）
     */
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

    /**
     * 从数据库中的字符串值转换为枚举
     * @param dbValue 数据库里的 status（如 "pending"）
     * @return 对应的 OrderStatus，没有匹配时返回 null
     */
    public static OrderStatus fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (OrderStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(dbValue)) {
                return status;
            }
        }
        return null;
    }
}