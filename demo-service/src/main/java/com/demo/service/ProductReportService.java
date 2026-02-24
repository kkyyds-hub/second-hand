package com.demo.service;

import com.demo.dto.admin.ResolveProductReportRequest;
import com.demo.dto.user.ProductReportRequest;
import com.demo.dto.user.ProductReportResponse;

/**
 * 商品举报工单服务接口（Day16 Step4）。
 */
public interface ProductReportService {

    /**
     * 买家提交商品举报工单。
     */
    ProductReportResponse createReport(Long reporterId, Long productId, ProductReportRequest request);

    /**
     * 管理员处理举报工单。
     * 返回值：
     * - 首次处理：工单处理成功
     * - 重复处理：工单已处理
     */
    String resolveReport(Long resolverId, String ticketNo, ResolveProductReportRequest request);
}

