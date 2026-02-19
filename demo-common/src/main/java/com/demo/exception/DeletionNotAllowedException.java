package com.demo.exception;

/**
 * 删除受限异常。
 * 用于表达当前资源不允许删除的业务场景。
 */
public class DeletionNotAllowedException extends BaseException {

    /**
     * 使用异常消息构造删除受限异常。
     */
    public DeletionNotAllowedException(String msg) {
        super(msg);
    }

}
