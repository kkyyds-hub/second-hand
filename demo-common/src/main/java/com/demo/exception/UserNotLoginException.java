package com.demo.exception;

/**
 * 用户未登录异常。
 */
public class UserNotLoginException extends BaseException {

    /**
     * 使用默认构造器创建异常。
     */
    public UserNotLoginException() {
    }

    /**
     * 使用异常消息构造未登录异常。
     */
    public UserNotLoginException(String msg) {
        super(msg);
    }

}
