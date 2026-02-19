package com.demo.enumeration;

/**
 * 商品域动作类型（Day16）
 * <p>
 * 说明：
 * 1) 该枚举用于统一“动作”口径，避免 Service/审计/事件各自维护一套字符串。
 * 2) code 字段建议直接作为审计 action 与事件 payload 的动作标识。
 */
public enum ProductActionType {

    APPROVE("approve", "审核通过"),
    REJECT("reject", "审核驳回"),
    OFF_SHELF("off_shelf", "卖家下架"),
    WITHDRAW("withdraw", "撤回审核"),
    RESUBMIT("resubmit", "重新提审"),
    ON_SHELF("on_shelf", "上架入口（提审别名）"),
    EDIT("edit", "编辑商品"),
    FORCE_OFF_SHELF("force_off_shelf", "管理员强制下架"),
    SOLD("sold", "成交置已售");

    /** 编码值。 */
    private final String code;
    /** 业务语义说明。 */
    private final String description;

    ProductActionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取动作编码。
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取动作说明。
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码解析对应枚举类型。
     */
    public static ProductActionType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("商品动作不能为空");
        }
        String normalized = code.trim();
        for (ProductActionType action : values()) {
            if (action.code.equalsIgnoreCase(normalized)) {
                return action;
            }
        }
        throw new IllegalArgumentException("非法商品动作: " + code);
    }
}
