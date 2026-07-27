-- P6-MQ-A-F1: 增量升级 mq_consume_log 以支持租约令牌和尝试计数。
--
-- 新增字段：
--   lease_token   VARCHAR(64) NULL   — 租约令牌，每次抢占生成 UUID
--   attempt_count INT DEFAULT 0      — 每次抢占 +1
--   last_error    VARCHAR(500) NULL  — 最近错误信息
--
-- 约束升级：
--   CHECK (status IN ('OK', 'FAIL', 'PROCESSING'))
--
-- 执行条件：
--   - 列不存在 → ADD COLUMN
--   - 列已存在 → 跳过
--   - 约束不含 PROCESSING → 重建约束
--   - 约束已含 PROCESSING → 跳过
--
-- 依赖：MySQL 8.0+
-- 不删除已有日志，不修改无关表。
-- 重复执行安全。

DROP PROCEDURE IF EXISTS migrate_p6_mq_consume_log_processing;

DELIMITER //

CREATE PROCEDURE migrate_p6_mq_consume_log_processing()
proc: BEGIN
    DECLARE col_exists INT DEFAULT 0;
    DECLARE chk_needs_fix INT DEFAULT 0;
    DECLARE tbl_exists INT DEFAULT 0;

    -- 检查表存在
    SELECT COUNT(*) INTO tbl_exists FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log';
    IF tbl_exists = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'P6-MQ-A-F1: mq_consume_log table not found, abort.';
    END IF;

    -- lease_token 列
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log' AND COLUMN_NAME = 'lease_token';
    IF col_exists = 0 THEN
        ALTER TABLE mq_consume_log ADD COLUMN lease_token VARCHAR(64) NULL AFTER status;
        SELECT 'P6-MQ-A-F1: added lease_token column' AS result;
    ELSE
        SELECT 'P6-MQ-A-F1: lease_token column exists, skip' AS result;
    END IF;

    -- attempt_count 列
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log' AND COLUMN_NAME = 'attempt_count';
    IF col_exists = 0 THEN
        ALTER TABLE mq_consume_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER lease_token;
        SELECT 'P6-MQ-A-F1: added attempt_count column' AS result;
    ELSE
        SELECT 'P6-MQ-A-F1: attempt_count column exists, skip' AS result;
    END IF;

    -- last_error 列
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log' AND COLUMN_NAME = 'last_error';
    IF col_exists = 0 THEN
        ALTER TABLE mq_consume_log ADD COLUMN last_error VARCHAR(500) NULL AFTER attempt_count;
        SELECT 'P6-MQ-A-F1: added last_error column' AS result;
    ELSE
        SELECT 'P6-MQ-A-F1: last_error column exists, skip' AS result;
    END IF;

    -- CHECK 约束：是否缺少 PROCESSING
    SELECT COUNT(*) INTO chk_needs_fix FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mq_consume_log'
      AND CONSTRAINT_NAME = 'chk_consume_log_status'
      AND CHECK_CLAUSE NOT LIKE '%PROCESSING%';

    IF chk_needs_fix > 0 THEN
        ALTER TABLE mq_consume_log DROP CHECK chk_consume_log_status;
        ALTER TABLE mq_consume_log
            ADD CONSTRAINT chk_consume_log_status
            CHECK (status IN ('OK', 'FAIL', 'PROCESSING'));
        SELECT 'P6-MQ-A-F1: CHECK constraint upgraded to include PROCESSING' AS result;
    ELSE
        SELECT 'P6-MQ-A-F1: CHECK constraint already includes PROCESSING, skip' AS result;
    END IF;

END //

DELIMITER ;

CALL migrate_p6_mq_consume_log_processing();

DROP PROCEDURE IF EXISTS migrate_p6_mq_consume_log_processing;
