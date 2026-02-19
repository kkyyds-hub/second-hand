package com.demo.exception;

/**
 * 账号被锁定异常
 */
public class AccountLockedException extends BaseException {

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public AccountLockedException() {
    }

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public AccountLockedException(String msg) {
        super(msg);
    }

}
