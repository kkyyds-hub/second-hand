package com.demo.concurrency;

import com.demo.entity.Order;
import com.demo.entity.OrderShipReminderTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderShipReminderTaskMapper;
import com.demo.service.OrderNoticeService;
import com.demo.service.serviceimpl.OrderShipReminderTaskProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-S3：发货提醒任务并发分流回归。
 */
@ExtendWith(MockitoExtension.class)
class OrderShipReminderTaskProcessorConcurrencyTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderShipReminderTaskMapper reminderTaskMapper;
    @Mock
    private OrderNoticeService orderNoticeService;

    @InjectMocks
    private OrderShipReminderTaskProcessor processor;

    @Test
    void shouldTreatMarkSuccessRowsZeroAsIdempotentWhenLatestAlreadySuccess() {
        OrderShipReminderTask task = new OrderShipReminderTask();
        task.setId(101L);
        task.setOrderId(90011L);
        task.setLevel("H1");
        task.setRetryCount(0);
        task.setDeadlineTime(LocalDateTime.now().plusHours(1));

        Order order = new Order();
        order.setId(90011L);
        order.setStatus("paid");

        OrderShipReminderTask latest = new OrderShipReminderTask();
        latest.setId(101L);
        latest.setStatus("SUCCESS");

        when(orderMapper.selectOrderForReminder(90011L)).thenReturn(order);
        when(reminderTaskMapper.markSuccess(any(), any(), anyString())).thenReturn(0);
        when(reminderTaskMapper.selectById(101L)).thenReturn(latest);

        boolean processed = processor.processOne(task);

        Assertions.assertFalse(processed);
        verify(reminderTaskMapper, never()).markFail(any(), any(), anyString());
    }

    @Test
    void shouldReturnFalseWhenOrderAlreadyTerminalAndCancelCasMiss() {
        OrderShipReminderTask task = new OrderShipReminderTask();
        task.setId(102L);
        task.setOrderId(90012L);
        task.setLevel("H6");
        task.setRetryCount(0);
        task.setDeadlineTime(LocalDateTime.now().plusHours(6));

        Order order = new Order();
        order.setId(90012L);
        order.setStatus("cancelled");

        OrderShipReminderTask latest = new OrderShipReminderTask();
        latest.setId(102L);
        latest.setStatus("CANCELLED");

        when(orderMapper.selectOrderForReminder(90012L)).thenReturn(order);
        when(reminderTaskMapper.markCancelled(102L)).thenReturn(0);
        when(reminderTaskMapper.selectById(102L)).thenReturn(latest);

        boolean processed = processor.processOne(task);

        Assertions.assertFalse(processed);
        verify(reminderTaskMapper, never()).markFail(any(), any(), anyString());
    }
}
