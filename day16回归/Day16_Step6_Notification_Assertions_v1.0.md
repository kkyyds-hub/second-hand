# Day16 Step6 通知链路断言清单（Outbox + MQ + Mongo）

- 版本：v1.0
- 日期：2026-02-22
- 适用范围：Day16 第六步“事件与通知”

---

## 1. 断言目标

1. 业务主流程成功后，Outbox 必有对应事件记录。
2. MQ 消费成功后，Mongo 站内信落库成功。
3. 通知失败不影响主流程事务结果。
4. 重复消费不产生重复站内信（幂等生效）。

---

## 2. 场景与事件映射

1. 管理员审核通过/驳回
- 触发事件：`PRODUCT_REVIEWED`
- 路由键：`product.reviewed`
- 通知对象：商品 owner

2. 管理员强制下架
- 触发事件：`PRODUCT_FORCE_OFF_SHELF`
- 路由键：`product.force.off.shelf`
- 通知对象：商品 owner

3. 管理员处理举报单
- 触发事件：`PRODUCT_REPORT_RESOLVED`
- 路由键：`product.report.resolved`
- 通知对象：reporter

---

## 3. Outbox 断言

1. `message_outbox.event_type` 与预期事件一致。
2. `message_outbox.routing_key` 与预期路由键一致。
3. `message_outbox.status` 从 `NEW/FAIL` 最终转为 `SENT`。
4. `payload_json` 可反序列化为 `EventMessage`，且 `payload` 关键字段完整。

---

## 4. 消费幂等断言

1. 同一 `eventId` 重复投递时：
- `mq_consume_log` 仅有一条有效处理记录（同 consumer + eventId 唯一）。
- Mongo 只产生一条对应 `clientMsgId` 的消息。

2. 人工重复投递同一事件，站内信总数不增长。

---

## 5. Mongo 站内信断言

1. `fromUserId = 0`（系统通知）。
2. `orderId = 0`（系统通知槽位）。
3. `toUserId` 命中目标用户（owner 或 reporter）。
4. `clientMsgId` 前缀符合约定：
- `SYS-PRODUCT-REVIEWED-`
- `SYS-PRODUCT-FORCE-OFF-SHELF-`
- `SYS-PRODUCT-REPORT-RESOLVED-`

---

## 6. 开关断言（灰度控制）

配置项：

- `product.notice.reviewed-enabled`
- `product.notice.force-off-shelf-enabled`
- `product.notice.report-resolved-enabled`

断言：

1. 关闭开关后，消费者 ACK 且不写 Mongo。
2. 开关恢复后，新事件可正常写 Mongo。

---

## 7. 异常容错断言

1. 暂停 Mongo 或制造消费异常：
- 主接口仍返回业务成功（不回滚主流程）。
- 消费记录进入 `FAIL` 或 DLQ（按现网策略）。

2. 恢复依赖后：
- 可通过重试机制补发消息。

---

## 8. 关注日志关键字

1. `PRODUCT_REVIEWED`
2. `PRODUCT_FORCE_OFF_SHELF`
3. `PRODUCT_REPORT_RESOLVED`
4. `Outbox sent success`

