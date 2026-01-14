package com.demo.enumeration;

/**
 * 信用分变更原因类型枚举
 */
public enum CreditReasonType {

    ORDER_COMPLETED("order_completed", "订单完成"),
    ORDER_CANCELLED("order_cancelled", "订单取消"),
    USER_VIOLATION("user_violation", "用户违规"),
    PRODUCT_VIOLATION("product_violation", "商品违规"),
    BAN_ACTIVE("ban_active", "封禁生效"),
    ADMIN_ADJUST("admin_adjust", "管理员调整"),
    RECALC("recalc", "重算/对账");

    private final String dbValue;
    private final String description;

    CreditReasonType(String dbValue, String description) {
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
     * 严格模式：仅接受 dbValue（给 Service/DB 用）
     * 非法值返回 null（与 OrderStatus 风格一致）
     *
     * @param dbValue 数据库存储值
     * @return 对应的原因类型，如果不存在则返回 null
     */
    public static CreditReasonType fromDbValue(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        for (CreditReasonType type : values()) {
            if (type.dbValue.equalsIgnoreCase(dbValue)) {
                return type;
            }
        }
        return null;
    }
}

