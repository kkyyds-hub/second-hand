package com.demo.exception;

/**
 * 购物车模块业务异常。
 */
public class ShoppingCartBusinessException extends BaseException {

    /**
     * 使用异常消息构造购物车业务异常。
     */
    public ShoppingCartBusinessException(String msg) {
        super(msg);
    }

}
