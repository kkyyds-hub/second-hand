package com.demo.order;

import com.demo.dto.payment.PaymentCallbackRequest;
import com.demo.entity.Order;
import com.demo.mapper.OrderMapper;
import com.demo.service.OrderService;
import com.demo.service.serviceimpl.OrderServiceImpl;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.payment.MockPaymentSimulationVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day20：mock 支付演示链路回归。
 *
 * 目标：
 * 1) 证明 `REPEAT` 场景会连续发两次成功回调；
 * 2) 证明两次回调复用同一个 tradeNo，符合“重复通知”语义；
 * 3) 证明 `/pay` 已经收口到 mock callback 主链，而不是直接改订单状态。
 */
@ExtendWith(MockitoExtension.class)
class OrderMockPaymentFlowTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderService orderServiceProxy;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "mockPaymentChannel", "mock");
        ReflectionTestUtils.setField(orderService, "mockPaymentSign", "unit-test-sign");
    }

    @Test
    void shouldSimulateRepeatCallbackWithSameTradeNo() {
        OrderDetail detail = buildDetail(101L, 2001L, "ORDER-101", "pending");
        Order before = buildOrder(101L, 2001L, "ORDER-101", "pending");
        Order after = buildOrder(101L, 2001L, "ORDER-101", "paid");

        when(orderMapper.getOrderDetail(101L, 2001L)).thenReturn(detail);
        when(orderMapper.selectOrderByOrderNo("ORDER-101")).thenReturn(before, after);
        when(orderServiceProxy.handlePaymentCallback(any(PaymentCallbackRequest.class)))
                .thenReturn("支付回调处理成功", "订单已支付，回调幂等成功");

        MockPaymentSimulationVO result = orderService.simulateMockPayment(101L, 2001L, "REPEAT");

        ArgumentCaptor<PaymentCallbackRequest> captor = ArgumentCaptor.forClass(PaymentCallbackRequest.class);
        verify(orderServiceProxy, times(2)).handlePaymentCallback(captor.capture());
        List<PaymentCallbackRequest> requests = captor.getAllValues();

        Assertions.assertEquals(2, result.getCallbackCount());
        Assertions.assertEquals("pending", result.getBeforeStatus());
        Assertions.assertEquals("paid", result.getAfterStatus());
        Assertions.assertEquals("支付回调处理成功", result.getFirstResult());
        Assertions.assertEquals("订单已支付，回调幂等成功", result.getSecondResult());
        Assertions.assertEquals("订单已支付，回调幂等成功", result.getFinalResult());
        Assertions.assertEquals(requests.get(0).getTradeNo(), requests.get(1).getTradeNo());
        Assertions.assertEquals("SUCCESS", requests.get(0).getStatus());
        Assertions.assertEquals("SUCCESS", requests.get(1).getStatus());
        Assertions.assertEquals("unit-test-sign", requests.get(0).getSign());
    }

    @Test
    void payOrderShouldReuseMockCallbackMainFlow() {
        OrderDetail detail = buildDetail(102L, 2002L, "ORDER-102", "pending");
        Order before = buildOrder(102L, 2002L, "ORDER-102", "pending");
        Order after = buildOrder(102L, 2002L, "ORDER-102", "paid");

        when(orderMapper.getOrderDetail(102L, 2002L)).thenReturn(detail);
        when(orderMapper.selectOrderByOrderNo("ORDER-102")).thenReturn(before, after);
        when(orderServiceProxy.handlePaymentCallback(any(PaymentCallbackRequest.class)))
                .thenReturn("支付回调处理成功");

        String result = orderService.payOrder(102L, 2002L);

        verify(orderServiceProxy, times(1)).handlePaymentCallback(any(PaymentCallbackRequest.class));
        Assertions.assertEquals("支付回调处理成功", result);
    }

    @Test
    void shouldRejectMockPaymentWhenCurrentUserIsNotBuyer() {
        OrderDetail detail = buildDetail(103L, 3003L, "ORDER-103", "pending");

        when(orderMapper.getOrderDetail(103L, 2003L)).thenReturn(detail);

        Assertions.assertThrows(RuntimeException.class,
                () -> orderService.simulateMockPayment(103L, 2003L, "SUCCESS"));
    }

    private OrderDetail buildDetail(Long orderId, Long buyerId, String orderNo, String status) {
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(orderId);
        detail.setBuyerId(buyerId);
        detail.setOrderNo(orderNo);
        detail.setStatus(status);
        detail.setTotalAmount(new BigDecimal("166.00"));
        return detail;
    }

    private Order buildOrder(Long orderId, Long buyerId, String orderNo, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setBuyerId(buyerId);
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("166.00"));
        return order;
    }
}
