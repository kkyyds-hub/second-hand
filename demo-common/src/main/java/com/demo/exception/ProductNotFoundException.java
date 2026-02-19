package com.demo.exception;

/**
 * 商品不存在异常。
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * 使用异常消息构造商品不存在异常。
     */
    public ProductNotFoundException(String message) {
        super(message);
    }

    /**
     * 使用异常消息与根因构造商品不存在异常。
     */
    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
