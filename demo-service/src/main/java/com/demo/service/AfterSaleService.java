package com.demo.service;

import com.demo.dto.aftersale.ArbitrateRequest;
import com.demo.dto.aftersale.CreateAfterSaleRequest;
import com.demo.dto.aftersale.DisputeRequest;
import com.demo.dto.aftersale.SellerDecisionRequest;

/**
 * Day13 Step5 - 售后服务
 */
public interface AfterSaleService {

    /**
     * 买家发起售后申请
     */
    Long createAfterSale(Long currentUserId, CreateAfterSaleRequest request);

    /**
     * 卖家处理售后（同意/拒绝）
     */
    String sellerDecision(Long afterSaleId, Long currentUserId, SellerDecisionRequest request);

    /**
     * 买家提交纠纷（平台介入）
     */
    String submitDispute(Long afterSaleId, Long currentUserId, DisputeRequest request);

    /**
     * 后台裁决
     */
    String arbitrate(Long afterSaleId, ArbitrateRequest request);
}
