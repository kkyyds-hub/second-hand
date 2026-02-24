package com.demo.enumeration;

/**
 * 商品举报工单状态枚举（Day16）。
 */
public enum ProductReportStatus {
    /** 待处理。 */
    PENDING("PENDING"),
    /** 举报成立并已处理。 */
    RESOLVED_VALID("RESOLVED_VALID"),
    /** 举报不成立并已处理。 */
    RESOLVED_INVALID("RESOLVED_INVALID");

    /** 数据库存储值。 */
    private final String code;

    ProductReportStatus(String code) {
        this.code = code;
    }

    /**
     * 获取状态编码。
     */
    public String getCode() {
        return code;
    }

    /**
     * 按编码解析状态（大小写不敏感）。
     */
    public static ProductReportStatus fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("举报工单状态不能为空");
        }
        String normalized = code.trim();
        for (ProductReportStatus status : values()) {
            if (status.code.equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法举报工单状态: " + code);
    }
}

