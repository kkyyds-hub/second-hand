package com.demo.enumeration;

/**
 * 评价角色枚举。
 */
public enum ReviewRole {

    /** 买家评价卖家。 */
    BUYER_TO_SELLER(1, "买家评价卖家");

    /** 编码值。 */
    private final int code;
    /** 业务语义说明。 */
    private final String desc;

    ReviewRole(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取角色编码。
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取角色描述。
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 按编码反查角色枚举。
     */
    public static ReviewRole fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReviewRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }
}
