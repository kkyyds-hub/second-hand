package com.demo.exception;

/**
 * 数据库更新异常。
 * 用于包装持久化层更新失败场景。
 */
public class DatabaseUpdateException extends RuntimeException {

    /**
     * 使用异常消息构造数据库更新异常。
     */
    public DatabaseUpdateException(String message) {
        super(message);
    }

    /**
     * 使用异常消息与根因构造数据库更新异常。
     */
    public DatabaseUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}

