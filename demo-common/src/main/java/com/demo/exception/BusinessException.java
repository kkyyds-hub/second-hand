package com.demo.exception;
public class BusinessException extends BaseException {
    private Integer code;
    public BusinessException(String message) { super(message); }
    public BusinessException(Integer code, String message) { super(message); this.code = code; }
    public Integer getCode() { return code; }
}
