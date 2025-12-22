package com.demo.enumeration;

public enum ProductStatus {

    UNDER_REVIEW("under_review", "审核中"),
    ON_SHELF("on_sale", "上架"),
    OFF_SHELF("off_shelf", "下架"),
    SOLD("sold", "已售");

    private final String dbValue;
    private final String cnName;

    ProductStatus(String dbValue, String cnName) {
        this.dbValue = dbValue;
        this.cnName = cnName;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getCnName() {
        return cnName;
    }

    /**
     * 严格模式：仅接受 dbValue（给 Service/DB 用）
     */
    public static ProductStatus fromDbValue(String dbValue) {
        if (dbValue == null || dbValue.trim().isEmpty()) {
            throw new IllegalArgumentException("商品状态不能为空");
        }
        String s = dbValue.trim();
        for (ProductStatus ps : values()) {
            if (ps.dbValue.equalsIgnoreCase(s)) {
                return ps;
            }
        }
        throw new IllegalArgumentException("非法商品状态(dbValue): " + dbValue);
    }

    /**
     * 兼容模式：接受中文 / dbValue / 枚举名（给 Controller 入参用）
     */
    public static ProductStatus fromAny(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("商品状态不能为空");
        }
        String s = input.trim();

        // 1) 先匹配 dbValue / 中文名 / 枚举名
        for (ProductStatus ps : values()) {
            if (ps.dbValue.equalsIgnoreCase(s)) return ps;
            if (ps.cnName.equals(s)) return ps;
            if (ps.name().equalsIgnoreCase(s)) return ps;
        }

        // 2) 兼容历史别名（可按你项目实际再补）
        if ("审核".equals(s) || "审核中".equals(s)) return UNDER_REVIEW;
        if ("上架中".equals(s) || "在售".equals(s)) return ON_SHELF;
        if ("下架中".equals(s)) return OFF_SHELF;

        // 兼容 on_sale 写法（如果你 DB 存的是 on_sale，可以保留这一条）
        if ("on_sale".equalsIgnoreCase(s)) return ON_SHELF;

        throw new IllegalArgumentException("非法商品状态: " + input);
    }

    /**
     * Controller 常用：把任何输入统一转成 dbValue
     */
    public static String normalizeToDbValue(String input) {
        return fromAny(input).getDbValue();
    }
}
