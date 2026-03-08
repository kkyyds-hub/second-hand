-- Day15 回归前置数据脚本（可重复执行）
-- 目标：
-- 1) 构造可用于 Day15 回归的订单基线（含 paid / 超时 paid）
-- 2) 预置发货提醒任务与超时任务，便于直接跑 admin run-once 接口
-- 3) 通过“定向清理 + 定向插入”实现幂等重跑
--
-- 适用库：secondhand2（请先 USE secondhand2;）
-- 注意：本脚本只改动 DAY15-REG-* 前缀的夹具数据，不会清空业务数据

SET NAMES utf8mb4;

START TRANSACTION;

-- ------------------------------------------------------------
-- 0) 基础参数（按你当前库的测试账号口径）
-- ------------------------------------------------------------
SET @buyer_id  := 1;  -- buyer01
SET @seller_id := 2;  -- seller01

-- 回归订单号（固定值，便于重复脚本覆盖）
SET @order_no_paid_ship    := 'DAY15-REG-PAID-SHIP';
SET @order_no_paid_timeout := 'DAY15-REG-PAID-TIMEOUT';

-- 回归商品 ID（专用，不污染你已有 DAY9/DAY10 夹具）
SET @product_ship_id    := 920001;
SET @product_timeout_id := 920002;

-- ------------------------------------------------------------
-- 1) 清理上一轮 DAY15 回归夹具（只清理本脚本生成的数据）
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_day15_reg_orders;
CREATE TEMPORARY TABLE tmp_day15_reg_orders (id BIGINT PRIMARY KEY);

INSERT INTO tmp_day15_reg_orders (id)
SELECT id
FROM orders
WHERE order_no IN (@order_no_paid_ship, @order_no_paid_timeout);

DELETE FROM order_refund_task
WHERE order_id IN (SELECT id FROM tmp_day15_reg_orders);

DELETE FROM order_ship_timeout_task
WHERE order_id IN (SELECT id FROM tmp_day15_reg_orders);

DELETE FROM order_ship_reminder_task
WHERE order_id IN (SELECT id FROM tmp_day15_reg_orders);

DELETE FROM order_items
WHERE order_id IN (SELECT id FROM tmp_day15_reg_orders);

DELETE FROM orders
WHERE id IN (SELECT id FROM tmp_day15_reg_orders);

DROP TEMPORARY TABLE IF EXISTS tmp_day15_reg_orders;

-- ------------------------------------------------------------
-- 2) 准备回归专用商品（UPSERT）
--    说明：状态先置为 sold，模拟“已下单占用库存”的真实场景。
-- ------------------------------------------------------------
INSERT INTO products (
    id, owner_id, title, description, price, images, category,
    status, view_count, reason, is_deleted, create_time, update_time
) VALUES
(
    @product_ship_id, @seller_id,
    'DAY15-REG-PRODUCT-SHIP', 'Day15 回归专用商品：发货链路',
    88.80, NULL, '回归测试',
    'sold', 0, NULL, 0, NOW(), NOW()
),
(
    @product_timeout_id, @seller_id,
    'DAY15-REG-PRODUCT-TIMEOUT', 'Day15 回归专用商品：超时链路',
    99.90, NULL, '回归测试',
    'sold', 0, NULL, 0, NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
    owner_id     = VALUES(owner_id),
    title        = VALUES(title),
    description  = VALUES(description),
    price        = VALUES(price),
    status       = 'sold',
    is_deleted   = 0,
    update_time  = NOW();

-- ------------------------------------------------------------
-- 3) 插入两笔回归订单
--    A. paid-ship：用于“卖家发货 + 物流查询 + 提醒任务”
--    B. paid-timeout：用于“超时关单 + 退款 + 处罚 + 通知”
-- ------------------------------------------------------------
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, shipping_company, tracking_no, shipping_remark,
    create_time, pay_time, complete_time, update_time, cancel_time, cancel_reason, ship_time, receive_time
) VALUES
(
    @order_no_paid_ship, @buyer_id, @seller_id, 88.80, 'paid',
    'Day15回归地址-发货链路（buyer01）', NULL, NULL, NULL,
    NOW() - INTERVAL 30 MINUTE,
    NOW() - INTERVAL 30 MINUTE,
    NULL, NOW(), NULL, NULL, NULL, NULL
),
(
    @order_no_paid_timeout, @buyer_id, @seller_id, 99.90, 'paid',
    'Day15回归地址-超时链路（buyer01）', NULL, NULL, NULL,
    NOW() - INTERVAL 50 HOUR,
    NOW() - INTERVAL 50 HOUR,
    NULL, NOW(), NULL, NULL, NULL, NULL
);

-- 拿到新订单 ID
SELECT id INTO @order_id_paid_ship
FROM orders WHERE order_no = @order_no_paid_ship LIMIT 1;

SELECT id INTO @order_id_paid_timeout
FROM orders WHERE order_no = @order_no_paid_timeout LIMIT 1;

-- ------------------------------------------------------------
-- 4) 插入订单明细（order_items）
-- ------------------------------------------------------------
INSERT INTO order_items (
    order_id, product_id, price, quantity,
    product_title_snapshot, product_thumbnail_snapshot, create_time, update_time
) VALUES
(
    @order_id_paid_ship, @product_ship_id, 88.80, 1,
    'DAY15-REG-PRODUCT-SHIP', NULL, NOW(), NOW()
),
(
    @order_id_paid_timeout, @product_timeout_id, 99.90, 1,
    'DAY15-REG-PRODUCT-TIMEOUT', NULL, NOW(), NOW()
);

-- ------------------------------------------------------------
-- 5) 插入发货超时任务（仅超时链路订单）
--    deadline = pay_time + 48h；本订单 pay_time 已是 50h 前 => 到期可执行
-- ------------------------------------------------------------
INSERT INTO order_ship_timeout_task (
    order_id, deadline_time, status, retry_count, next_retry_time, last_error, create_time, update_time
) VALUES
(
    @order_id_paid_timeout,
    (SELECT pay_time + INTERVAL 48 HOUR FROM orders WHERE id = @order_id_paid_timeout),
    'PENDING', 0, NULL, NULL, NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
    deadline_time   = VALUES(deadline_time),
    status          = 'PENDING',
    retry_count     = 0,
    next_retry_time = NULL,
    last_error      = NULL,
    update_time     = NOW();

-- ------------------------------------------------------------
-- 6) 插入发货提醒任务（H24/H6/H1）
--    这里把 remind_time 都设置成过去时间，方便你 run-once 立即处理
-- ------------------------------------------------------------
-- paid-ship 订单提醒任务
SET @deadline_ship := (SELECT pay_time + INTERVAL 48 HOUR FROM orders WHERE id = @order_id_paid_ship);

INSERT INTO order_ship_reminder_task (
    order_id, seller_id, level, deadline_time, remind_time,
    status, retry_count, running_at, sent_at, client_msg_id, last_error, create_time, update_time
) VALUES
(@order_id_paid_ship, @seller_id, 'H24', @deadline_ship, NOW() - INTERVAL 2 MINUTE, 'PENDING', 0, NULL, NULL, NULL, NULL, NOW(), NOW()),
(@order_id_paid_ship, @seller_id, 'H6',  @deadline_ship, NOW() - INTERVAL 2 MINUTE, 'PENDING', 0, NULL, NULL, NULL, NULL, NOW(), NOW()),
(@order_id_paid_ship, @seller_id, 'H1',  @deadline_ship, NOW() - INTERVAL 2 MINUTE, 'PENDING', 0, NULL, NULL, NULL, NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    deadline_time = VALUES(deadline_time),
    remind_time   = VALUES(remind_time),
    status        = 'PENDING',
    retry_count   = 0,
    running_at    = NULL,
    sent_at       = NULL,
    client_msg_id = NULL,
    last_error    = NULL,
    update_time   = NOW();

-- paid-timeout 订单提醒任务
SET @deadline_timeout := (SELECT pay_time + INTERVAL 48 HOUR FROM orders WHERE id = @order_id_paid_timeout);

INSERT INTO order_ship_reminder_task (
    order_id, seller_id, level, deadline_time, remind_time,
    status, retry_count, running_at, sent_at, client_msg_id, last_error, create_time, update_time
) VALUES
(@order_id_paid_timeout, @seller_id, 'H24', @deadline_timeout, NOW() - INTERVAL 2 MINUTE, 'PENDING', 0, NULL, NULL, NULL, NULL, NOW(), NOW()),
(@order_id_paid_timeout, @seller_id, 'H6',  @deadline_timeout, NOW() - INTERVAL 2 MINUTE, 'PENDING', 0, NULL, NULL, NULL, NULL, NOW(), NOW()),
(@order_id_paid_timeout, @seller_id, 'H1',  @deadline_timeout, NOW() - INTERVAL 2 MINUTE, 'PENDING', 0, NULL, NULL, NULL, NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    deadline_time = VALUES(deadline_time),
    remind_time   = VALUES(remind_time),
    status        = 'PENDING',
    retry_count   = 0,
    running_at    = NULL,
    sent_at       = NULL,
    client_msg_id = NULL,
    last_error    = NULL,
    update_time   = NOW();

COMMIT;

-- ------------------------------------------------------------
-- 7) 输出回归基线摘要（给 Postman/Newman 环境变量赋值）
-- ------------------------------------------------------------
SELECT
    @order_id_paid_ship    AS paid_ship_order_id,
    @order_id_paid_timeout AS paid_timeout_order_id,
    @product_ship_id       AS paid_ship_product_id,
    @product_timeout_id    AS paid_timeout_product_id;

SELECT
    status, COUNT(*) AS cnt
FROM orders
WHERE id IN (@order_id_paid_ship, @order_id_paid_timeout)
GROUP BY status;

SELECT
    order_id, level, status, remind_time, deadline_time
FROM order_ship_reminder_task
WHERE order_id IN (@order_id_paid_ship, @order_id_paid_timeout)
ORDER BY order_id, level;

SELECT
    order_id, status, deadline_time
FROM order_ship_timeout_task
WHERE order_id = @order_id_paid_timeout;
