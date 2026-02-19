package com.demo.exception;

/**
 * 密码修改失败异常
 */
public class PasswordEditFailedException extends BaseException {

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public PasswordEditFailedException(String msg) {
        super(msg);
    }

}
