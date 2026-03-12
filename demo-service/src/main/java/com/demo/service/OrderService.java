package com.demo.service;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.payment.PaymentCallbackRequest;
import com.demo.dto.user.CancelOrderRequest;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.result.PageResult;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.demo.vo.payment.MockPaymentSimulationVO;
import com.github.pagehelper.PageInfo;
import org.springframework.transaction.annotation.Transactional;

/**
 * OrderService 接口。
 */
public interface OrderService {

    PageResult<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId);

    PageResult<SellerOrderSummary> getSellOrder(PageQueryDTO pageQueryDTO, Long currentUserId);

    OrderDetail getOrderDetail(Long orderId, Long currentUserId);

    String shipOrder(Long orderId, ShipOrderRequest request, Long currentUserId);

    String confirmOrder(Long orderId, Long currentUserId);

    CreateOrderResponse createOrder(CreateOrderRequest request, Long currentUserId);

    String payOrder(Long orderId, Long currentUserId);

    /**
     * Day20 - 模拟支付回调演示入口。
     *
     * @param orderId 订单 ID
     * @param currentUserId 当前登录用户 ID（必须是买家）
     * @param scenario 演示场景：SUCCESS / FAIL / REPEAT
     * @return 回调演示结果（含前后状态、每次回调结果）
     */
    MockPaymentSimulationVO simulateMockPayment(Long orderId, Long currentUserId, String scenario);

    String cancelOrder(Long orderId, CancelOrderRequest request, Long currentUserId);

    /**
     * Day13 Step2 - 处理支付回调（幂等 + 占位验签）
     */
    String handlePaymentCallback(PaymentCallbackRequest request);

}
