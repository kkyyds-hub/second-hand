package com.demo.constant;

/**
 * 评价/审核模块常量定义。
 */
public class ReviewConstants {

    /**
     * 工具类私有构造，禁止实例化。
     */
    private ReviewConstants() {}

    /**
     * 订单+角色唯一索引名。
     */
    public static final String UNIQUE_KEY_ORDER_ROLE = "uniq_order_role";

    /**
     * 角色：买家评价卖家。
     */
    public static final int ROLE_BUYER_TO_SELLER = 1;

    /**
     * 非匿名评价标记。
     */
    public static final int ANON_NO = 0;

    /**
     * 匿名评价标记。
     */
    public static final int ANON_YES = 1;

    /**
     * 匿名展示名。
     */
    public static final String ANON_DISPLAY_NAME = "匿名用户";

    /**
     * 匿名头像地址。
     * 当前冻结为空字符串，前端使用默认头像。
     */
    public static final String ANON_AVATAR = "";
}
