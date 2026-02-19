package com.demo.service;

import com.demo.vo.order.OrderLogisticsVO;

/**
 * 订单物流服务。
 *
 * 对外提供“订单维度”的物流信息读取能力：
 * - 订单快照字段（物流公司、运单号、发货时间）
 * - 动态轨迹字段（provider 查询）
 */
public interface LogisticsService {

    /**
     * 获取订单物流详情。
     *
     * @param orderId 订单 ID
     * @param currentUserId 当前登录用户 ID（用于权限校验）
     * @return 物流视图对象
     */
    OrderLogisticsVO getOrderLogistics(Long orderId, Long currentUserId);
}
