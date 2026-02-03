package com.demo.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ID_NOT_FOUND = "用户不存在";
    public static final String BANNED_EXISTS = "用户用户已被封禁";
    public static final String PRODUCT_NOT_FOUND = "商品不存在或已被删除";
    public static final String FAVORITE_ONLY_ON_SALE = "仅在售商品允许收藏";
    public static final String ALREADY_EXISTS = "已存在";
    // ===== Day12 Review =====
    public static final String REVIEW_NO_PERMISSION = "无权评价该订单";
    public static final String REVIEW_ORDER_NOT_COMPLETED = "订单未完成，无法评价";
    public static final String REVIEW_ALREADY_EXISTS = "该订单已评价";
    public static final String REVIEW_RATING_INVALID = "评分必须为1~5";
    public static final String REVIEW_CONTENT_INVALID = "评价内容不少于10个字，且不超过500字";
    public static final String REVIEW_ANON_NAME = "匿名用户";
    public static final String REVIEW_CONTENT_TOO_SHORT = "评价内容不少于10个字";
    public static final String REVIEW_CONTENT_TOO_LONG = "评价内容不超过500字";
    public static final String REVIEW_TOO_FREQUENT = "评价过于频繁，请稍后再试";
    public static final String MESSAGE_NO_PERMISSION = "无权在该订单中发送消息";

}
