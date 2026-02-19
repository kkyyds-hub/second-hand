package com.demo.exception;

/**
 * 登录失败
 */
public class LoginFailedException extends BaseException {
    /**
     * 构造函数，初始化当前组件依赖。
     */
    public LoginFailedException(String msg) {
        super(msg);
    }
}
