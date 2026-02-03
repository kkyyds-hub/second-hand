package com.demo.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Day13 Step7 - 统计服务
 */
public interface StatisticsService {

    /**
     * 统计 DAU（日活跃用户数）
     * @param date 统计日期
     * @return 活跃用户数
     */
    Long countDAU(LocalDate date);

    /**
     * 统计商品发布量
     * @param date 统计日期
     * @return 发布量（可按 category 分解）
     */
    Map<String, Object> countProductPublish(LocalDate date);

    /**
     * 统计成交订单量与 GMV
     * @param date 统计日期
     * @return 包含成交订单量和GMV
     */
    Map<String, Object> countOrderAndGMV(LocalDate date);
}
