package com.demo.context;

/**
 * 当前请求线程上下文。
 * 用于在同一次请求链路中传递当前登录用户 ID。
 */
public class BaseContext {

    /**
     * 存放当前线程用户 ID。
     */
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /**
     * 设置当前线程用户 ID。
     */
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    /**
     * 获取当前线程用户 ID。
     */
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    /**
     * 清理当前线程上下文，防止线程复用导致数据串用。
     */
    public static void removeCurrentId() {
        threadLocal.remove();
    }

}
