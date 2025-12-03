package com.demo.enumeration;

public enum ProductStatus {

    ON_SHELF("on_sale"),
    SOLD("sold"),
    OFF_SHELF("off_shelf"),
    UNDER_REVIEW("under_review");

    // 数据库存的实际值
    private final String dbValue;

    // 构造方法（必须是 private 或省略访问修饰符）
    ProductStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    // 可选：根据数据库里的值反查枚举
    public static ProductStatus fromDbValue(String value) {
        for (ProductStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知商品状态: " + value);
    }
}
