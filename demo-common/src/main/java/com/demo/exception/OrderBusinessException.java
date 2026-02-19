package com.demo.exception;

/**
 * 订单模块业务异常。
 */
public class OrderBusinessException extends BaseException {

    /**
     * 使用异常消息构造订单业务异常。
     */
    public OrderBusinessException(String msg) {
        super(msg);
    }

}
