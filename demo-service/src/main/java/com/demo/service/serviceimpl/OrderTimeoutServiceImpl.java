package com.demo.service.serviceimpl;

import com.demo.mapper.OrderMapper;
import com.demo.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeTimeoutOrderAndRelease(Long orderId, LocalDateTime deadline) {
        int rows = orderMapper.closeTimeoutOrder(orderId, deadline);
        if (rows == 1) {
            orderMapper.releaseProductsForOrder(orderId);
            return true;
        }
        return false;
    }
}
