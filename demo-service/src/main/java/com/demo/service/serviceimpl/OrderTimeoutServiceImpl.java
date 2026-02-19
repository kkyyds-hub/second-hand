package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.enumeration.CreditReasonType;
import com.demo.mapper.OrderMapper;
import com.demo.service.CreditService;
import com.demo.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单超时处理服务实现。
 */
@Service
@RequiredArgsConstructor
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final CreditService creditService;

    /**
     * 关闭超时未支付订单并释放商品占用。
     * 同时触发买家信用分重算（取消订单场景）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeTimeoutOrderAndRelease(Long orderId, LocalDateTime deadline) {
        int rows = orderMapper.closeTimeoutOrder(orderId, deadline);
        if (rows == 1) {
            orderMapper.releaseProductsForOrder(orderId);

            // 超时关单本质是取消订单，会影响买家取消订单维度统计。
            Order order = orderMapper.selectOrderBasicById(orderId);
            if (order != null && order.getBuyerId() != null) {
                creditService.recalcUserCredit(order.getBuyerId(), CreditReasonType.ORDER_CANCELLED, orderId);
            }
            return true;
        }
        return false;
    }
}
