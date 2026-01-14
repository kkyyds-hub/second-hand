package com.demo.constant;

/**
 * 信用体系常量类
 * <p>
 * 分数范围：0 ~ 200（闭区间）
 * 默认值：DEFAULT_SCORE = 100（对应信用等级 LV3）
 * <p>
 * 等级阈值（与 CreditLevel 枚举一致）：
 * - LV1: 0 ~ 39
 * - LV2: 40 ~ 79
 * - LV3: 80 ~ 119
 * - LV4: 120 ~ 159
 * - LV5: 160 ~ 200
 * <p>
 * 原因类型枚举（CreditReasonType）：
 * - ORDER_COMPLETED: 订单完成
 * - ORDER_CANCELLED: 订单取消
 * - USER_VIOLATION: 用户违规
 * - PRODUCT_VIOLATION: 商品违规
 * - BAN_ACTIVE: 封禁生效
 * - ADMIN_ADJUST: 管理员调整
 * - RECALC: 重算/对账
 * <p>
 * Day12 扩展点说明：
 * 引入"评价"后，可追加维度：好评率、纠纷次数、退货/退款等。
 * 建议做法：新增统计来源与策略后，仍保持 CreditLevel 映射不轻易变动。
 * 若要变动阈值，必须同步更新：CreditLevel + CreditConstants + 相关文档。
 */
public class CreditConstants {

    /**
     * 默认信用分数
     */
    public static final int DEFAULT_SCORE = 100;

    /**
     * 信用分数最小值
     */
    public static final int SCORE_MIN = 0;

    /**
     * 信用分数最大值
     */
    public static final int SCORE_MAX = 200;

    /**
     * 订单完成加分
     */
    public static final int DELTA_ORDER_COMPLETED = 2;

    /**
     * 订单取消扣分
     */
    public static final int DELTA_ORDER_CANCELLED = -3;

    /**
     * 用户违规扣分
     */
    public static final int DELTA_USER_VIOLATION = -10;

    /**
     * 商品违规扣分（暂未使用，预留）
     */
    public static final int DELTA_PRODUCT_VIOLATION = -5;

    /**
     * 封禁生效扣分
     */
    public static final int DELTA_BAN_ACTIVE = -30;
}

