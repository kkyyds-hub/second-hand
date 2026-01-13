package com.demo.constant;

/**
 * 信用策略（与计分规则解耦）
 */
public class CreditPolicyConstants {

    /**
     * LV2 最大活跃商品数（under_review + on_sale）
     */
    public static final int MAX_ACTIVE_PRODUCTS_LV2 = 3;

    private CreditPolicyConstants() {}
}
