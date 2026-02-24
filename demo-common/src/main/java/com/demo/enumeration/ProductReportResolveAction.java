package com.demo.enumeration;

/**
 * 商品举报工单处理动作枚举（Day16）。
 */
public enum ProductReportResolveAction {
    /** 举报不成立。 */
    DISMISS("dismiss"),
    /** 举报成立并强制下架。 */
    FORCE_OFF_SHELF("force_off_shelf");

    /** 动作编码。 */
    private final String code;

    ProductReportResolveAction(String code) {
        this.code = code;
    }

    /**
     * 获取动作编码。
     */
    public String getCode() {
        return code;
    }

    /**
     * 按编码解析动作（大小写不敏感）。
     */
    public static ProductReportResolveAction fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("举报处理动作不能为空");
        }
        String normalized = code.trim();
        for (ProductReportResolveAction action : values()) {
            if (action.code.equalsIgnoreCase(normalized)) {
                return action;
            }
        }
        throw new IllegalArgumentException("非法举报处理动作: " + code);
    }
}

