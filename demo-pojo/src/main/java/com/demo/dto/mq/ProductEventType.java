package com.demo.dto.mq;

/**
 * Day16 - 商品治理事件类型枚举。
 *
 * 事件面向“审核/下架/举报处理”三条主链路：
 * 1) PRODUCT_REVIEWED：审核通过/驳回
 * 2) PRODUCT_FORCE_OFF_SHELF：管理员强制下架
 * 3) PRODUCT_REPORT_RESOLVED：举报工单处理完成
 */
public enum ProductEventType {
    /** 商品审核完成（通过或驳回） */
    PRODUCT_REVIEWED("PRODUCT_REVIEWED"),
    /** 管理员强制下架完成 */
    PRODUCT_FORCE_OFF_SHELF("PRODUCT_FORCE_OFF_SHELF"),
    /** 举报工单处理完成 */
    PRODUCT_REPORT_RESOLVED("PRODUCT_REPORT_RESOLVED");

    /** 事件编码 */
    private final String code;

    ProductEventType(String code) {
        this.code = code;
    }

    /**
     * 获取事件编码。
     */
    public String getCode() {
        return code;
    }
}
