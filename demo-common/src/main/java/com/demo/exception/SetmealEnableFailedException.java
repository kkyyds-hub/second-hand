package com.demo.exception;

/**
 * 套餐启用失败异常
 */
public class SetmealEnableFailedException extends BaseException {

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public SetmealEnableFailedException() {
    }

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public SetmealEnableFailedException(String msg) {
        super(msg);
    }
}
