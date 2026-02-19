package com.demo.constant;

/**
 * 商品域业务提示常量
 * <p>
 * 约束：
 * 1) 商品模块优先使用本常量，减少文案散落与口径漂移。
 * 2) 仅承载“商品治理”高频业务文案，非商品域文案不放在此类中。
 */
public class ProductMessageConstant {

    /**
     * 工具类私有构造，禁止实例化。
     */
    private ProductMessageConstant() {
    }

    public static final String PRODUCT_NOT_FOUND_OR_DELETED = "商品不存在或已被删除";
    public static final String PRODUCT_NOT_FOUND_OR_UNAVAILABLE = "商品不存在或不可查看";
    public static final String PRODUCT_UPDATE_RETRY = "更新失败，请重试";
    public static final String PRODUCT_STATUS_UPDATE_FAILED = "商品状态更新失败";

    public static final String PRODUCT_NO_PERMISSION_VIEW = "无权查看该商品详情";
    public static final String PRODUCT_NO_PERMISSION_EDIT = "无权修改该商品";
    public static final String PRODUCT_NO_PERMISSION_OPERATE = "无权操作该商品";

    public static final String PRODUCT_REJECT_REASON_REQUIRED = "驳回原因不能为空";
    public static final String PRODUCT_REJECT_REASON_TOO_LONG = "驳回原因长度不能超过200";

    public static final String PRODUCT_ONLY_UNDER_REVIEW_CAN_APPROVE = "仅审核中商品可通过，当前状态: ";
    public static final String PRODUCT_ONLY_UNDER_REVIEW_CAN_REJECT = "仅审核中商品可驳回，当前状态: ";

    public static final String PRODUCT_SOLD_CANNOT_EDIT = "商品已售出，不能编辑";
    public static final String PRODUCT_STATUS_NOT_ALLOW_EDIT = "当前状态不允许编辑";
    public static final String PRODUCT_STATUS_NOT_ALLOW_OFF_SHELF = "当前状态不允许下架";

    public static final String PRODUCT_SOLD_CANNOT_DELETE = "已售商品不可删除";
    public static final String PRODUCT_ON_SALE_DELETE_NEED_OFF_SHELF = "在售商品请先下架再删除";
    public static final String PRODUCT_DELETE_FAILED_RETRY = "删除失败，请重试";

    public static final String PRODUCT_SOLD_CANNOT_RESUBMIT = "已售商品不可提审";
    public static final String PRODUCT_ON_SALE_NO_NEED_RESUBMIT = "在售商品无需提审";
    public static final String PRODUCT_STATUS_CANNOT_RESUBMIT = "当前状态无法重新提交审核";

    public static final String PRODUCT_SOLD_CANNOT_ON_SHELF = "已售商品不可上架";
    public static final String PRODUCT_ON_SALE_NO_NEED_ON_SHELF = "在售商品无需上架";
    public static final String PRODUCT_STATUS_CANNOT_ON_SHELF = "当前状态无法重新上架";

    public static final String PRODUCT_SOLD_CANNOT_WITHDRAW = "已售商品不可撤回";
    public static final String PRODUCT_ON_SALE_NEED_OFF_SHELF_FIRST = "在售商品需先下架";
    public static final String PRODUCT_ALREADY_WITHDRAWN = "商品已撤回";
    public static final String PRODUCT_STATUS_NO_NEED_WITHDRAW = "当前状态无需撤回审核";

    public static final String PRODUCT_CONTENT_SENSITIVE_SUBMIT = "商品内容包含敏感词，无法提交：";
    public static final String PRODUCT_CONTENT_SENSITIVE_CREATE = "商品内容包含敏感词，无法发布：";
    public static final String PRODUCT_CREATE_FAILED_RETRY = "商品创建失败，请重试";
    public static final String PRODUCT_ID_REQUIRED = "productId 不能为空";
}
