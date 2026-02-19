package com.demo.enumeration;

/**
 * 用户状态枚举。
 */
public enum UserStatus {

    /** 未激活。 */
    INACTIVE,
    /** 正常激活。 */
    ACTIVE,
    /** 冻结。 */
    FROZEN,
    /** 封禁。 */
    BANNED;

    /**
     * 将任意大小写状态字符串转换为枚举。
     */
    public static UserStatus from(String status) {
        if (status == null) return null;
        return UserStatus.valueOf(status.toUpperCase());
    }
}
