package com.demo.vo.admin;

import lombok.Data;

/**
 * Dashboard 待审核商品队列专用 VO。
 *
 * 2026-03-16 起单独拆出本对象，而不是继续复用 ProductReview 的列表 DTO：
 * 1. 首页只需要“展示友好”的审核队列字段，不应该被 ProductDTO 的历史兼容问题牵连；
 * 2. 这里显式使用 sellerName，避免沿用 user 这类语义过宽的字段，减少前后端误解；
 * 3. 后续如果 Dashboard 需要补充卖家等级、命中规则等首页专属字段，可直接在此扩展。
 */
@Data
public class AdminDashboardReviewQueueItemVO {

    /** 页面展示用记录编号。 */
    private String id;

    /** 待审核商品名称。 */
    private String item;

    /** 商品所属卖家展示名。 */
    private String sellerName;

    /** 审核类型或商品分类标签。 */
    private String type;

    /** 页面展示用价格文案。 */
    private String price;

    /** 相对提交时间。 */
    private String time;

    /** 首页轻量风险等级。 */
    private String risk;
}
