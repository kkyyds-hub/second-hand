package com.demo.concurrency;

import com.demo.entity.Order;
import com.demo.entity.OrderShipTimeoutTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.mapper.OrderShipTimeoutTaskMapper;
import com.demo.service.OrderNoticeService;
import com.demo.service.OrderShipTimeoutPenaltyService;
import com.demo.service.serviceimpl.OrderShipTimeoutTaskProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

/**
 * P4-S3：发货超时任务并发分流回归。
 */
@ExtendWith(MockitoExtension.class)
class OrderShipTimeoutTaskProcessorConcurrencyTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderShipTimeoutTaskMapper taskMapper;
    @Mock
    private OrderRefundTaskMapper refundTaskMapper;
    @Mock
    private OrderNoticeService orderNoticeService;
    @Mock
    private OrderShipTimeoutPenaltyService penaltyService;

    @InjectMocks
    private OrderShipTimeoutTaskProcessor processor;

    @Test
    void shouldReturnFalseWhenOrderTerminalAndCancelCasMiss() {
        OrderShipTimeoutTask task = new OrderShipTimeoutTask();
        task.setId(201L);
        task.setOrderId(90101L);

        Order order = new Order();
        order.setId(90101L);
        order.setStatus("shipped");

        OrderShipTimeoutTask latestTask = new OrderShipTimeoutTask();
        latestTask.setId(201L);
        latestTask.setOrderId(90101L);
        latestTask.setStatus("CANCELLED");

        when(orderMapper.selectOrderBasicById(90101L)).thenReturn(order);
        when(taskMapper.markCancelled(201L)).thenReturn(0);
        when(taskMapper.selectByOrderId(90101L)).thenReturn(latestTask);

        boolean closed = processor.processOne(task);

        Assertions.assertFalse(closed);
    }
}
