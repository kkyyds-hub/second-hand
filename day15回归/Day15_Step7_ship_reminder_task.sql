-- Day15 Step7：发货超时提醒任务表（order_ship_reminder_task）
-- 执行前请确认当前库已切换到业务库（如 secondhand2）

CREATE TABLE IF NOT EXISTS `order_ship_reminder_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单 ID',
    `seller_id` BIGINT NOT NULL COMMENT '卖家ID（提醒接收人）',
    `level` VARCHAR(8) NOT NULL COMMENT '提醒档位：H24/H6/H1',
    `deadline_time` DATETIME NOT NULL COMMENT '发货截止时间（pay_time + 48h）',
    `remind_time` DATETIME NOT NULL COMMENT '计划提醒时间',
    `status` VARCHAR(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '累计重试次数',
    `running_at` DATETIME DEFAULT NULL COMMENT '进入RUNNING时间，用于回收卡死任务',
    `sent_at` DATETIME DEFAULT NULL COMMENT '发送成功时间',
    `client_msg_id` VARCHAR(128) DEFAULT NULL COMMENT '站内信幂等键',
    `last_error` VARCHAR(255) DEFAULT NULL COMMENT '最近失败原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_level` (`order_id`, `level`),
    KEY `idx_status_remind_time` (`status`, `remind_time`),
    KEY `idx_status_running_at` (`status`, `running_at`),
    KEY `idx_seller_status` (`seller_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货超时提醒任务表';

