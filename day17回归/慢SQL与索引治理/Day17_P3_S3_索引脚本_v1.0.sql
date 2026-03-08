-- Day17 P3-S3 索引脚本 v1.0
-- 目标：补齐高频查询复合索引，降低 filesort/回表风险。
-- 适用库：secondhand2（默认使用当前 DATABASE()）

USE secondhand2;

-- 1) addresses：支撑用户地址分页排序 + 默认地址查询
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'addresses'
              AND index_name = 'idx_addr_user_default_updated'
        ),
        'SELECT ''[skip] idx_addr_user_default_updated already exists'' AS msg',
        'ALTER TABLE `addresses` ADD INDEX `idx_addr_user_default_updated` (`user_id`,`is_default`,`updated_at`,`id`)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) product_violations：支撑按商品+状态分页（倒序）
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'product_violations'
              AND index_name = 'idx_pv_product_status_id'
        ),
        'SELECT ''[skip] idx_pv_product_status_id already exists'' AS msg',
        'ALTER TABLE `product_violations` ADD INDEX `idx_pv_product_status_id` (`product_id`,`status`,`id`)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) message_outbox：补充 status/next_retry_time/id 复合索引，降低高频扫描成本
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'message_outbox'
              AND index_name = 'idx_outbox_status_retry_id'
        ),
        'SELECT ''[skip] idx_outbox_status_retry_id already exists'' AS msg',
        'ALTER TABLE `message_outbox` ADD INDEX `idx_outbox_status_retry_id` (`status`,`next_retry_time`,`id`)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 校验索引是否生效
SELECT table_name,
       index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS idx_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('addresses', 'product_violations', 'message_outbox')
  AND index_name IN ('idx_addr_user_default_updated', 'idx_pv_product_status_id', 'idx_outbox_status_retry_id')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- 5) 回滚语句（按需手工执行）
-- ALTER TABLE `addresses` DROP INDEX `idx_addr_user_default_updated`;
-- ALTER TABLE `product_violations` DROP INDEX `idx_pv_product_status_id`;
-- ALTER TABLE `message_outbox` DROP INDEX `idx_outbox_status_retry_id`;
