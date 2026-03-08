-- Day15 Step7：超时处罚幂等索引
-- 目标：同一卖家在同一订单上的 ship_timeout 处罚只记录一次。

-- 1) 给 user_violations 增加业务 ID 字段（用于绑定订单 ID）
--    若列已存在，可忽略报错或手动改为 IF NOT EXISTS 版本（视 MySQL 版本而定）。
ALTER TABLE user_violations
ADD COLUMN biz_id BIGINT NULL COMMENT '业务ID（如订单 ID）' AFTER user_id;

-- 2) 创建幂等唯一键：user_id + violation_type + biz_id
--    仅当 biz_id 非空时生效；biz_id 为空的历史人工处罚不受影响。
ALTER TABLE user_violations
ADD UNIQUE KEY uk_user_violation_biz (user_id, violation_type, biz_id);

-- 3) 上线前可选检查（确认没有历史冲突）
SELECT user_id, violation_type, biz_id, COUNT(*) AS cnt
FROM user_violations
WHERE biz_id IS NOT NULL
GROUP BY user_id, violation_type, biz_id
HAVING COUNT(*) > 1;


