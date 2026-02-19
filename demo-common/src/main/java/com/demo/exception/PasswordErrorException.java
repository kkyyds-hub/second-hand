package com.demo.exception;

/**
 * 密码错误异常
 */
public class PasswordErrorException extends BaseException {

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public PasswordErrorException() {
    }

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public PasswordErrorException(String msg) {
        super(msg);
    }

}
