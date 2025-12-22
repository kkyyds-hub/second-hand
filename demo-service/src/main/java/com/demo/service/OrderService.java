package com.demo.service;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.result.PageResult;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.github.pagehelper.PageInfo;

public interface OrderService {

    PageResult<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId);

    PageResult<SellerOrderSummary> getSellOrder(PageQueryDTO pageQueryDTO, Long currentUserId);

    OrderDetail getOrderDetail(Long orderId, Long currentUserId);

    void shipOrder(Long orderId, ShipOrderRequest request, Long currentUserId);

    void confirmOrder(Long orderId, Long currentUserId);

    CreateOrderResponse createOrder(CreateOrderRequest request, Long currentUserId);
}
