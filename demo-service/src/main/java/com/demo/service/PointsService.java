package com.demo.service;

import com.demo.result.PageResult;

/**
 * Day13 Step8 - 积分服务
 */
public interface PointsService {

    /**
     * 查询用户积分总额
     */
    Integer getTotalPoints(Long userId);

    /**
     * 查询用户积分流水（分页）
     */
    PageResult<com.demo.entity.PointsLedger> listPoints(Long userId, Integer page, Integer pageSize);

    /**
     * 订单完成发放积分（买卖双方各+N）
     */
    void grantPointsForOrderComplete(Long orderId, Long buyerId, Long sellerId);
}
