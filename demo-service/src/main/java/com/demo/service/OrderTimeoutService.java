package com.demo.service;


import java.time.LocalDateTime;

/**
 * 订单超时关闭服务接口。
 */
public interface OrderTimeoutService {

    /**
     * 关闭超时订单并释放相关资源。
     *
     * @param orderId 目标订单 ID
     * @param deadline 调用方传入的超时截止时间，用于幂等判断
     * @return 本次调用成功关闭订单返回 {@code true}，否则返回 {@code false}
     */
    boolean closeTimeoutOrderAndRelease(Long orderId, LocalDateTime deadline);
}
