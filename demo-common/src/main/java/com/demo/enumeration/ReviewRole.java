package com.demo.enumeration;

public enum ReviewRole {

    BUYER_TO_SELLER(1, "买家评价卖家");

    private final int code;
    private final String desc;

    ReviewRole(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }

    public String getDesc() { return desc; }

    public static ReviewRole fromCode(Integer code) {
        if (code == null) return null;
        for (ReviewRole r : values()) {
            if (r.code == code) return r;
        }
        return null;
    }
}
