package com.demo.service;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.github.pagehelper.PageInfo;

public interface OrderService {

    PageInfo<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId);

    PageInfo<SellerOrderSummary> getSellOrder(PageQueryDTO pageQueryDTO, Long currentUserId);

    OrderDetail getOrderDetail(Long orderId, Long currentUserId);

    void shipOrder(Long orderId, ShipOrderRequest request, Long currentUserId);

    void confirmOrder(Long orderId, Long currentUserId);
}
