package com.demo.exception;

/**
 * 地址簿模块业务异常。
 */
public class AddressBookBusinessException extends BaseException {

    /**
     * 使用异常消息构造地址簿业务异常。
     */
    public AddressBookBusinessException(String msg) {
        super(msg);
    }

}
