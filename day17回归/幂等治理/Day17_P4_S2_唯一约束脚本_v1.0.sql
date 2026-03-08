-- Day17 P4-S2 幂等唯一约束脚本 v1.0
-- 目标：把核心幂等键落到数据库唯一约束，保证“重复请求不产生重复副作用”。
-- 适用库：secondhand2（默认使用当前 DATABASE()）

USE secondhand2;

-- 1) 订单号唯一：订单主业务键
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'orders'
              AND index_name = 'uk_orders_order_no'
        ),
        'SELECT ''[skip] uk_orders_order_no already exists'' AS msg',
        'ALTER TABLE `orders` ADD UNIQUE KEY `uk_orders_order_no` (`order_no`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) Outbox 事件幂等：event_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'message_outbox'
              AND index_name = 'uk_event_id'
        ),
        'SELECT ''[skip] uk_event_id already exists'' AS msg',
        'ALTER TABLE `message_outbox` ADD UNIQUE KEY `uk_event_id` (`event_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) MQ 消费去重：consumer + event_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'mq_consume_log'
              AND index_name = 'uk_consumer_event'
        ),
        'SELECT ''[skip] uk_consumer_event already exists'' AS msg',
        'ALTER TABLE `mq_consume_log` ADD UNIQUE KEY `uk_consumer_event` (`consumer`,`event_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 订单标记幂等：order_id + type 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'order_flags'
              AND index_name = 'uniq_order_type'
        ),
        'SELECT ''[skip] uniq_order_type already exists'' AS msg',
        'ALTER TABLE `order_flags` ADD UNIQUE KEY `uniq_order_type` (`order_id`,`type`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) 发货超时任务幂等：order_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'order_ship_timeout_task'
              AND index_name = 'uk_ship_timeout_order_id'
        ),
        'SELECT ''[skip] uk_ship_timeout_order_id already exists'' AS msg',
        'ALTER TABLE `order_ship_timeout_task` ADD UNIQUE KEY `uk_ship_timeout_order_id` (`order_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) 发货提醒任务幂等：order_id + level 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'order_ship_reminder_task'
              AND index_name = 'uk_order_level'
        ),
        'SELECT ''[skip] uk_order_level already exists'' AS msg',
        'ALTER TABLE `order_ship_reminder_task` ADD UNIQUE KEY `uk_order_level` (`order_id`,`level`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7) 退款任务幂等：order_id + refund_type 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'order_refund_task'
              AND index_name = 'uk_refund_order_type'
        ),
        'SELECT ''[skip] uk_refund_order_type already exists'' AS msg',
        'ALTER TABLE `order_refund_task` ADD UNIQUE KEY `uk_refund_order_type` (`order_id`,`refund_type`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8) 退款任务业务幂等键：idempotency_key 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'order_refund_task'
              AND index_name = 'uk_refund_idempotency'
        ),
        'SELECT ''[skip] uk_refund_idempotency already exists'' AS msg',
        'ALTER TABLE `order_refund_task` ADD UNIQUE KEY `uk_refund_idempotency` (`idempotency_key`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 9) 积分流水幂等：user_id + biz_type + biz_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'points_ledger'
              AND index_name = 'uniq_points_biz'
        ),
        'SELECT ''[skip] uniq_points_biz already exists'' AS msg',
        'ALTER TABLE `points_ledger` ADD UNIQUE KEY `uniq_points_biz` (`user_id`,`biz_type`,`biz_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 10) 评价幂等：order_id + role 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'reviews'
              AND index_name = 'uniq_order_role'
        ),
        'SELECT ''[skip] uniq_order_role already exists'' AS msg',
        'ALTER TABLE `reviews` ADD UNIQUE KEY `uniq_order_role` (`order_id`,`role`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 11) 违规记录幂等：user_id + violation_type + biz_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'user_violations'
              AND index_name = 'uk_user_violation_biz'
        ),
        'SELECT ''[skip] uk_user_violation_biz already exists'' AS msg',
        'ALTER TABLE `user_violations` ADD UNIQUE KEY `uk_user_violation_biz` (`user_id`,`violation_type`,`biz_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 12) 钱包流水幂等：biz_type + biz_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'wallet_transactions'
              AND index_name = 'uk_wallet_tx_biz_type_biz_id'
        ),
        'SELECT ''[skip] uk_wallet_tx_biz_type_biz_id already exists'' AS msg',
        'ALTER TABLE `wallet_transactions` ADD UNIQUE KEY `uk_wallet_tx_biz_type_biz_id` (`biz_type`,`biz_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 13) 收藏幂等：user_id + product_id 唯一
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'favorites'
              AND index_name = 'uk_favorites_user_product'
        ),
        'SELECT ''[skip] uk_favorites_user_product already exists'' AS msg',
        'ALTER TABLE `favorites` ADD UNIQUE KEY `uk_favorites_user_product` (`user_id`,`product_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 14) 售后幂等：order_id 唯一（每单只允许一条售后主记录）
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'after_sales'
              AND index_name = 'uniq_order'
        ),
        'SELECT ''[skip] uniq_order already exists'' AS msg',
        'ALTER TABLE `after_sales` ADD UNIQUE KEY `uniq_order` (`order_id`)'
    )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 15) 校验结果
SELECT table_name,
       index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS idx_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND (
        (table_name = 'orders' AND index_name = 'uk_orders_order_no')
        OR (table_name = 'message_outbox' AND index_name = 'uk_event_id')
        OR (table_name = 'mq_consume_log' AND index_name = 'uk_consumer_event')
        OR (table_name = 'order_flags' AND index_name = 'uniq_order_type')
        OR (table_name = 'order_ship_timeout_task' AND index_name = 'uk_ship_timeout_order_id')
        OR (table_name = 'order_ship_reminder_task' AND index_name = 'uk_order_level')
        OR (table_name = 'order_refund_task' AND index_name IN ('uk_refund_order_type', 'uk_refund_idempotency'))
        OR (table_name = 'points_ledger' AND index_name = 'uniq_points_biz')
        OR (table_name = 'reviews' AND index_name = 'uniq_order_role')
        OR (table_name = 'user_violations' AND index_name = 'uk_user_violation_biz')
        OR (table_name = 'wallet_transactions' AND index_name = 'uk_wallet_tx_biz_type_biz_id')
        OR (table_name = 'favorites' AND index_name = 'uk_favorites_user_product')
        OR (table_name = 'after_sales' AND index_name = 'uniq_order')
      )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- 16) 回滚语句（按需手工执行）
-- ALTER TABLE `orders` DROP INDEX `uk_orders_order_no`;
-- ALTER TABLE `message_outbox` DROP INDEX `uk_event_id`;
-- ALTER TABLE `mq_consume_log` DROP INDEX `uk_consumer_event`;
-- ALTER TABLE `order_flags` DROP INDEX `uniq_order_type`;
-- ALTER TABLE `order_ship_timeout_task` DROP INDEX `uk_ship_timeout_order_id`;
-- ALTER TABLE `order_ship_reminder_task` DROP INDEX `uk_order_level`;
-- ALTER TABLE `order_refund_task` DROP INDEX `uk_refund_order_type`;
-- ALTER TABLE `order_refund_task` DROP INDEX `uk_refund_idempotency`;
-- ALTER TABLE `points_ledger` DROP INDEX `uniq_points_biz`;
-- ALTER TABLE `reviews` DROP INDEX `uniq_order_role`;
-- ALTER TABLE `user_violations` DROP INDEX `uk_user_violation_biz`;
-- ALTER TABLE `wallet_transactions` DROP INDEX `uk_wallet_tx_biz_type_biz_id`;
-- ALTER TABLE `favorites` DROP INDEX `uk_favorites_user_product`;
-- ALTER TABLE `after_sales` DROP INDEX `uniq_order`;
