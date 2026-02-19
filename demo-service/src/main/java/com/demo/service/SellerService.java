package com.demo.service;

import com.demo.dto.user.SellerSummaryDTO;

/**
 * 卖家中心聚合服务接口。
 */
public interface SellerService {

    /**
     * 按卖家 ID 查询卖家中心统计摘要。
     */
    SellerSummaryDTO getSummary(Long sellerId);
}
