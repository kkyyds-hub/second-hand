package com.demo.service.serviceimpl;

import com.demo.dto.seller.SellerOrderCountDTO;
import com.demo.dto.seller.SellerProductCountDTO;
import com.demo.dto.user.SellerSummaryDTO;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.ProductMapper;
import com.demo.service.SellerService;
import com.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 卖家中心聚合服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SellerServiceImpl implements SellerService {

    private final UserService userService;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    /**
     * 统计当前卖家的商品与订单摘要数据。
     */
    @Override
    public SellerSummaryDTO getSummary(Long sellerId) {
        // 入口只信入参：sellerId 来自 Controller 的 currentUserId
        userService.requireSeller(sellerId);

        SellerProductCountDTO productStats = productMapper.countProductsBySellerId(sellerId);
        SellerOrderCountDTO orderStats = orderMapper.countOrdersBySellerId(sellerId);

        SellerSummaryDTO dto = new SellerSummaryDTO();

        // products
        dto.setTotalProducts(nz(productStats == null ? null : productStats.getTotalProducts()));
        dto.setUnderReviewProducts(nz(productStats == null ? null : productStats.getUnderReviewProducts()));
        dto.setOnSaleProducts(nz(productStats == null ? null : productStats.getOnSaleProducts()));
        dto.setOffShelfProducts(nz(productStats == null ? null : productStats.getOffShelfProducts()));
        dto.setSoldProducts(nz(productStats == null ? null : productStats.getSoldProducts()));

        // orders
        dto.setTotalOrders(nz(orderStats == null ? null : orderStats.getTotalOrders()));
        dto.setPendingOrders(nz(orderStats == null ? null : orderStats.getPendingOrders()));
        dto.setPaidOrders(nz(orderStats == null ? null : orderStats.getPaidOrders()));
        dto.setShippedOrders(nz(orderStats == null ? null : orderStats.getShippedOrders()));
        dto.setCompletedOrders(nz(orderStats == null ? null : orderStats.getCompletedOrders()));
        dto.setCancelledOrders(nz(orderStats == null ? null : orderStats.getCancelledOrders()));

        return dto;
    }

    /**
     * 空值转 0。
     */
    private Long nz(Long v) {
        return v == null ? 0L : v;
    }
}

