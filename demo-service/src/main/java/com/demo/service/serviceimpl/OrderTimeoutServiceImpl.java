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

@Service
@RequiredArgsConstructor
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final CreditService creditService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeTimeoutOrderAndRelease(Long orderId, LocalDateTime deadline) {
        int rows = orderMapper.closeTimeoutOrder(orderId, deadline);
        if (rows == 1) {
            orderMapper.releaseProductsForOrder(orderId);

            // Step3：超时关单本质上也是取消（cancelled），会影响买家的 cancelled 统计
            Order order = orderMapper.selectOrderBasicById(orderId);
            if (order != null && order.getBuyerId() != null) {
                creditService.recalcUserCredit(order.getBuyerId(), CreditReasonType.ORDER_CANCELLED, orderId);
            }
            return true;
        }
        return false;
    }
}
