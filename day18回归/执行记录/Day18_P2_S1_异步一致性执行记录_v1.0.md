# Day18 P2-S1 异步一致性执行记录 v1.0

- 日期：2026-02-25
- 关联复现文档：`day18回归/执行复现步骤/Day18_P2_S1_主成功异步不丢_执行复现_v1.0.md`
- 执行方式：管理员接口 + SQL 核验 + 受控失败注入（测试库）。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. 数据库：`secondhand2`
3. 执行人：`Codex`
4. 执行时间：`2026-02-25 11:23:57`
5. 管理员账号：`13900000001`

---

## 2. 演练对象

1. 订单：`orderId=900039`（`order_no=2026022510533011735`，`status=paid`）
2. 事件：`eventId=42729be2-95e8-43c6-9adc-78ce82a8df39`
3. Outbox 主键：`id=36`（`event_type=ORDER_PAID`）

---

## 3. 前置快照

1. 订单状态：`900039|2026022510533011735|paid|88.00`
2. Outbox：`36|42729be2-95e8-43c6-9adc-78ce82a8df39|SENT|0|NULL`
3. 消费计数（`OrderPaidConsumer + eventId`）：`1`
4. 订单提醒任务数（`order_ship_reminder_task`）：`3`
5. 消费日志重复组数（全局）：`0`

---

## 4. 执行动作与结果

### 4.1 场景 A：主成功后异步不丢（失败恢复）

1. 演练注入：将目标 Outbox 事件置为 `FAIL` 且可立即重试（测试库单条更新）。
2. 调用：
   - `POST /admin/ops/outbox/event/{eventId}/trigger-now`
   - 返回：`updatedRows=1, success=true`
3. 调用：
   - `POST /admin/ops/outbox/publish-once?limit=50`
   - 返回：`pulled=1, sent=1, failed=0`
4. 后置状态：
   - Outbox：`36|42729be2-95e8-43c6-9adc-78ce82a8df39|SENT|1|NULL`

结论：主业务成功后，异步事件可通过 Outbox 补偿重试收敛为 `SENT`，链路不丢。

### 4.2 场景 B：重复消息不产生重复副作用

1. 重放后消费计数（`OrderPaidConsumer + eventId`）：`1`（未增加）
2. 重放后提醒任务数（`order_id=900039`）：`3`（未增加）
3. 全局重复组扫描：
   - `mq_consume_log_dup_groups=0`
   - `outbox_event_dup_groups=0`

结论：重复发布同一事件后，消费端幂等生效，无重复副作用。

---

## 5. SQL 核验结果（执行后）

1. 索引核验：
   - `message_outbox.uk_event_id(event_id)` 存在
   - `mq_consume_log.uk_consumer_event(consumer,event_id)` 存在
2. 状态分布：
   - `message_outbox`：`SENT:37`
3. 重复组：
   - `mq_consume_log`：`0`
   - `message_outbox(event_id)`：`0`

---

## 6. DoD 勾选

- [x] 可证明“主成功、异步不丢”。  
- [x] 重复消息不会产生重复业务副作用。  
- [x] 已形成可复现执行证据（接口返回 + SQL 结果）。  

---

（文件结束）
