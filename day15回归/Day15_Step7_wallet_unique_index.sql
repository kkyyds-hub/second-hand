-- Day15 Step7：退款记账幂等索引
-- 目标：保障 wallet_transactions 在 ORDER_REFUND 场景下
--      同一业务键（biz_type + biz_id）只能入账一次。

-- 1) 上线前先检查是否存在重复数据（若有需先清理）
SELECT biz_type, biz_id, COUNT(*) AS cnt
FROM wallet_transactions
WHERE biz_type = 'ORDER_REFUND'
GROUP BY biz_type, biz_id
HAVING COUNT(*) > 1;

-- 2) 添加唯一约束（防重复入账）
-- 注意：若上一步有重复数据，此语句会失败。
ALTER TABLE wallet_transactions
ADD UNIQUE KEY uk_wallet_tx_biz_type_biz_id (biz_type, biz_id);

