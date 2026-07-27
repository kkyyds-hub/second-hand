-- P6-MQ-A: 增量升级 mq_consume_log CHECK 约束以支持 PROCESSING 状态。
--
-- 背景：
--   所有 MQ 消费者在幂等抢占阶段插入 status='PROCESSING' 后，
--   再根据业务结果更新为 'OK' 或 'FAIL'。
--
-- 执行前状态：
--   CHECK (status IN ('OK', 'FAIL'))
--
-- 执行后状态：
--   CHECK (status IN ('OK', 'FAIL', 'PROCESSING'))
--
-- 重复执行安全：
--   - 若约束已包含 'PROCESSING'，ALTER TABLE 在 MySQL 8.0.19+ 会报错。
--   - 建议首次升级前执行："SHOW CREATE TABLE mq_consume_log\G" 确认。
--   - 本脚本以存储过程包装，仅在旧约束仍存在时执行。
--
-- 依赖：MySQL 8.0.19+（ALTER TABLE ... DROP CHECK 语法）。
-- 不删除已有日志，不修改无关表。

DELIMITER //

CREATE PROCEDURE IF NOT EXISTS migrate_p6_mq_consume_log_processing()
BEGIN
    DECLARE cnt INT DEFAULT 0;

    -- 检查当前 CHECK 约束是否不含 PROCESSING
    SELECT COUNT(*) INTO cnt
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mq_consume_log'
      AND CHECK_CLAUSE NOT LIKE '%PROCESSING%';

    IF cnt > 0 THEN
        ALTER TABLE mq_consume_log DROP CHECK chk_consume_log_status;

        ALTER TABLE mq_consume_log
            ADD CONSTRAINT chk_consume_log_status
            CHECK (status IN ('OK', 'FAIL', 'PROCESSING'));

        SELECT 'P6-MQ-A: mq_consume_log CHECK constraint upgraded to include PROCESSING' AS result;
    ELSE
        SELECT 'P6-MQ-A: mq_consume_log CHECK constraint already includes PROCESSING, skipped.' AS result;
    END IF;
END //

DELIMITER ;

-- 执行
CALL migrate_p6_mq_consume_log_processing();

-- 清理（可选）
DROP PROCEDURE IF EXISTS migrate_p6_mq_consume_log_processing;
