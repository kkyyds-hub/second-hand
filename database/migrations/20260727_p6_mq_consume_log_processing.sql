-- P6-MQ-A-F2: 增量升级 mq_consume_log 支持租约令牌和 PROCESSING 状态。
--
-- 执行前必须停止所有消费者！
-- 部署顺序：
--   1) 停止后端消费者
--   2) 执行本脚本
--   3) 部署新版本
--   4) 启动消费者
--
-- 新增列：
--   lease_token   VARCHAR(64) NULL   — 租约令牌 UUID
--   attempt_count INT DEFAULT 0      — 每次抢占 +1
--   last_error    VARCHAR(500) NULL  — 最近错误信息
--
-- 约束升级：
--   CHECK (status IN ('OK', 'FAIL', 'PROCESSING'))
--
-- 遗留数据处理：
--   旧 PROCESSING + lease_token IS NULL → FAIL（新消费者 retry）
--
-- 依赖：MySQL 8.0+
-- 不删除已有日志，不修改无关表。重复执行安全。

DROP PROCEDURE IF EXISTS migrate_p6_mq_consume_log_processing;

DELIMITER //

CREATE PROCEDURE migrate_p6_mq_consume_log_processing()
proc: BEGIN
    DECLARE col_exists INT DEFAULT 0;
    DECLARE chk_exists INT DEFAULT 0;
    DECLARE chk_missing_processing INT DEFAULT 0;
    DECLARE tbl_exists INT DEFAULT 0;
    DECLARE legacy_count INT DEFAULT 0;

    -- Step 0: 检查表存在
    SELECT COUNT(*) INTO tbl_exists FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log';
    IF tbl_exists = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'P6-MQ-A-F2: mq_consume_log table not found, abort.';
    END IF;

    -- Step 1: lease_token 列
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log'
      AND COLUMN_NAME = 'lease_token';
    IF col_exists = 0 THEN
        ALTER TABLE mq_consume_log ADD COLUMN lease_token VARCHAR(64) NULL AFTER status;
        SELECT 'P6-MQ-A-F2: added lease_token column' AS result;
    ELSE
        SELECT 'P6-MQ-A-F2: lease_token column exists, skip' AS result;
    END IF;

    -- Step 2: attempt_count 列
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log'
      AND COLUMN_NAME = 'attempt_count';
    IF col_exists = 0 THEN
        ALTER TABLE mq_consume_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER lease_token;
        SELECT 'P6-MQ-A-F2: added attempt_count column' AS result;
    ELSE
        SELECT 'P6-MQ-A-F2: attempt_count column exists, skip' AS result;
    END IF;

    -- Step 3: last_error 列
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_consume_log'
      AND COLUMN_NAME = 'last_error';
    IF col_exists = 0 THEN
        ALTER TABLE mq_consume_log ADD COLUMN last_error VARCHAR(500) NULL AFTER attempt_count;
        SELECT 'P6-MQ-A-F2: added last_error column' AS result;
    ELSE
        SELECT 'P6-MQ-A-F2: last_error column exists, skip' AS result;
    END IF;

    -- Step 4: 检查 CHECK 约束是否存在
    SELECT COUNT(*) INTO chk_exists FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mq_consume_log'
      AND CONSTRAINT_NAME = 'chk_consume_log_status'
      AND CONSTRAINT_TYPE = 'CHECK';

    IF chk_exists = 0 THEN
        -- 约束不存在 → 新增
        ALTER TABLE mq_consume_log
            ADD CONSTRAINT chk_consume_log_status
            CHECK (status IN ('OK', 'FAIL', 'PROCESSING'));
        SELECT 'P6-MQ-A-F2: CHECK constraint created' AS result;
    ELSE
        -- 约束存在 → 检查是否包含 PROCESSING
        SELECT COUNT(*) INTO chk_missing_processing
        FROM information_schema.CHECK_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_NAME = 'chk_consume_log_status'
          AND CHECK_CLAUSE NOT LIKE '%PROCESSING%';

        IF chk_missing_processing > 0 THEN
            ALTER TABLE mq_consume_log DROP CHECK chk_consume_log_status;
            ALTER TABLE mq_consume_log
                ADD CONSTRAINT chk_consume_log_status
                CHECK (status IN ('OK', 'FAIL', 'PROCESSING'));
            SELECT 'P6-MQ-A-F2: CHECK constraint upgraded to include PROCESSING' AS result;
        ELSE
            SELECT 'P6-MQ-A-F2: CHECK constraint already includes PROCESSING, skip' AS result;
        END IF;
    END IF;

    -- Step 5: 处理遗留 PROCESSING（lease_token 为 NULL 或空）
    SELECT COUNT(*) INTO legacy_count FROM mq_consume_log
    WHERE status = 'PROCESSING' AND (lease_token IS NULL OR lease_token = '');

    IF legacy_count > 0 THEN
        UPDATE mq_consume_log
        SET status = 'FAIL',
            last_error = 'legacy PROCESSING without lease token migrated for retry',
            updated_at = NOW()
        WHERE status = 'PROCESSING'
          AND (lease_token IS NULL OR lease_token = '');

        SELECT CONCAT('P6-MQ-A-F2: migrated ', legacy_count, ' legacy PROCESSING records to FAIL for retry') AS result;
    ELSE
        SELECT 'P6-MQ-A-F2: no legacy PROCESSING records found, skip' AS result;
    END IF;

END //

DELIMITER ;

CALL migrate_p6_mq_consume_log_processing();

DROP PROCEDURE IF EXISTS migrate_p6_mq_consume_log_processing;
