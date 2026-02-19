package com.demo.job;

import com.demo.service.OrderShipReminderTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发货超时提醒任务 Job。
 *
 * 调度逻辑：
 * 1) 定时触发 reminderTaskService 扫描到期任务
 * 2) 仅做调度编排，不承载业务处理细节
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShipReminderTaskJob {

    private final OrderShipReminderTaskService reminderTaskService;

    @Value("${order.ship-reminder.enabled:true}")
    private boolean enabled;

    @Value("${order.ship-reminder.batch-size:200}")
    private int batchSize;

    /**
     * 定时触发发货提醒任务批处理。
     */
    @Scheduled(fixedDelayString = "${order.ship-reminder.fixed-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        int size = batchSize <= 0 ? 200 : batchSize;
        log.info("ship-reminder job start, batchSize={}", size);
        int success = reminderTaskService.processDueTasks(size);
        log.info("ship-reminder job finish, success={}", success);
    }
}
