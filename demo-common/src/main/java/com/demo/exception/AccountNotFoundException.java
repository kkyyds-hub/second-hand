package com.demo.exception;

/**
 * 账号不存在异常
 */
public class AccountNotFoundException extends BaseException {

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public AccountNotFoundException() {
    }

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public AccountNotFoundException(String msg) {
        super(msg);
    }

}
