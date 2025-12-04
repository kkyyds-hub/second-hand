package com.demo.enumeration;

public enum UserStatus {
    INACTIVE,
    ACTIVE,
    FROZEN,
    BANNED;

    public static UserStatus from(String status) {
        if (status == null) return null;
        return UserStatus.valueOf(status.toUpperCase());
    }
}
