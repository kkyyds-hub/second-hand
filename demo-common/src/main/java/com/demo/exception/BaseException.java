package com.demo.exception;

/**
 * 业务异常
 */
public class BaseException extends RuntimeException {

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public BaseException() {
    }

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public BaseException(String msg) {
        super(msg);
    }

}
