package com.demo.exception;

/**
 * 注册或登录流程异常
 */
public class RegistrationException extends BaseException {
    /**
     * 构造函数，初始化当前组件依赖。
     */
    public RegistrationException(String msg) {
        super(msg);
    }
}
