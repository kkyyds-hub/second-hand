package com.demo.exception;

/**
 * 通用业务异常。
 * 用于承载可预期的业务校验失败场景，并支持自定义错误码。
 */
public class BusinessException extends BaseException {

    private Integer code;

    /**
     * 仅使用异常消息构造业务异常。
     */
    public BusinessException(String message) { super(message); }

    /**
     * 使用错误码与异常消息构造业务异常。
     */
    public BusinessException(Integer code, String message) { super(message); this.code = code; }

    /**
     * 获取业务错误码。
     */
    public Integer getCode() { return code; }
}
