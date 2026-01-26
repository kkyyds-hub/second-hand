package com.demo.constant;

/**
 * 商品原因标记（对应 products.reason）
 * 用于后台驳回原因、卖家撤回审核等场景。
 *
 * 注意：这是 reason 维度，不是 status 维度。
 */
public final class ProductReason {
    private ProductReason() {}
    /** 卖家撤回审核 */
    public static final String SELLER_WITHDRAW = "seller_withdraw";
}
