package com.demo.concurrency;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.service.OrderNoticeService;
import com.demo.service.OrderRefundAccountingService;
import com.demo.service.serviceimpl.OrderRefundTaskProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-S3：退款任务并发分流回归。
 */
@ExtendWith(MockitoExtension.class)
class OrderRefundTaskProcessorConcurrencyTest {

    @Mock
    private OrderRefundTaskMapper refundTaskMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderNoticeService orderNoticeService;
    @Mock
    private OrderRefundAccountingService refundAccountingService;

    @InjectMocks
    private OrderRefundTaskProcessor processor;

    @Test
    void shouldTreatRowsZeroAsIdempotentWhenLatestAlreadySuccess() {
        OrderRefundTask task = new OrderRefundTask();
        task.setId(11L);
        task.setOrderId(9001L);
        task.setRefundType("ship_timeout");
        task.setStatus("PENDING");

        Order order = new Order();
        order.setId(9001L);
        order.setStatus("cancelled");

        OrderRefundTask latest = new OrderRefundTask();
        latest.setId(11L);
        latest.setStatus("SUCCESS");

        when(orderMapper.selectOrderBasicById(9001L)).thenReturn(order);
        when(refundTaskMapper.markSuccess(11L, "PENDING")).thenReturn(0);
        when(refundTaskMapper.selectById(11L)).thenReturn(latest);

        boolean processed = processor.processOne(task);

        Assertions.assertFalse(processed);
        verify(refundTaskMapper, never()).markFail(eq(11L), anyString(), any(), anyString());
    }

    @Test
    void shouldMarkFailWithExpectedStatusWhenAccountingThrows() {
        OrderRefundTask task = new OrderRefundTask();
        task.setId(12L);
        task.setOrderId(9002L);
        task.setRefundType("ship_timeout");
        task.setStatus("FAILED");

        Order order = new Order();
        order.setId(9002L);
        order.setStatus("cancelled");

        when(orderMapper.selectOrderBasicById(9002L)).thenReturn(order);
        doThrow(new RuntimeException("mock-error")).when(refundAccountingService).recordRefund(order, task);
        when(refundTaskMapper.markFail(eq(12L), eq("FAILED"), any(), anyString())).thenReturn(1);

        boolean processed = processor.processOne(task);

        Assertions.assertFalse(processed);
        verify(refundTaskMapper).markFail(eq(12L), eq("FAILED"), any(), anyString());
    }
}
