# Day18 P6-S2 并发保护方案 v1.0

- 日期：2026-02-26
- 对应阶段：`Step P6-S2：热点接口限流与削峰策略`
- 目标：让支付、回调、创建订单等高并发入口在突发流量下行为可预测、可降级、可恢复。

---

## 1. 现状基线（代码事实）

1. 同步链路已具备一致性保护：
   - 下单：商品原子占用 + 事务；
   - 支付/回调：状态 CAS + 幂等分流；
   - 任务链路：`insertIgnore + CAS + retry`。
2. 异步链路已具备削峰基础：
   - Outbox 定时发布（`5s`，批次默认 `50`）；
   - 任务 Job 固定频率（`60s`）+ `batch-size`；
   - Rabbit 消费端 `prefetch=20`，手动 ACK，失败入 DLQ。
3. 现状缺口：
   - 热点同步接口尚无统一限流门禁（网关/应用层）。

---

## 2. 热点入口识别（P6-S2 冻结）

| 类别 | 接口/链路 | 风险 |
|---|---|---|
| 下单 | `POST /user/orders` | 秒杀/刷单导致库存争抢、DB 热点 |
| 支付 | `POST /user/orders/{orderId}/pay` | 重复点击、并发支付风暴 |
| 回调 | `POST /payment/callback` | 三方重放/抖动引发瞬时洪峰 |
| Outbox 发布 | `OutboxPublishJob` | 消息积压时 DB+MQ 双侧压力 |
| 任务批处理 | ship-timeout/refund/reminder jobs | 大批量待处理造成突刺 |
| MQ 消费 | `OrderPaidConsumer`、`OrderStatusChangedConsumer` | 消费速度与上游不匹配 |

---

## 3. 分层并发保护策略

## 3.1 L1 网关限流（第一道闸）

建议在网关/Nginx 对热点路径做令牌桶（按 IP + URI）：

| 接口 | 建议阈值（初始） | 超限语义 |
|---|---:|---|
| `POST /user/orders` | `30 req/s`（IP），突发 `60` | `429` + 标准错误体 |
| `POST /user/orders/*/pay` | `20 req/s`（IP），突发 `40` | `429` + 标准错误体 |
| `POST /payment/callback` | `100 req/s`（IP），突发 `200` | 不直接丢弃业务语义，进入应用层二次判定 |

说明：回调接口不能仅靠 429 处理，避免触发支付平台雪崩重试。

## 3.2 L2 应用层限流（第二道闸）

应用层按“用户/订单/业务键”细粒度限制，保证公平性：

| 接口 | 维度 | 建议阈值（初始） | 超限返回 |
|---|---|---:|---|
| 创建订单 | `userId` | `5 次/10秒` | `429`，提示“下单过于频繁” |
| 支付订单 | `userId + orderId` | `3 次/10秒` | 命中则走幂等提示“订单已在处理中” |
| 支付回调 | `orderNo/tradeNo` | `1 次/5秒` 去重窗口 | 返回“已受理/幂等命中” |

实现建议：
1. Redis 令牌桶或固定窗口计数（优先 Redis，单机仅作开发兜底）。
2. 对 `pay/callback` 必须优先保证幂等语义，不可仅按频控直接失败。

## 3.3 L3 异步削峰（第三道闸）

| 链路 | 当前参数 | 削峰策略 |
|---|---|---|
| Outbox 发布 | `batch-size=50`,`fixedDelay=5s` | 高压时改 `batch=20~30` + 失败重试延迟拉长；低压恢复默认 |
| Outbox 告警 | fail=5/retry=10 | 保持与 P4-S2 一致，触发排障与人工补偿 |
| 任务 Job | `batch-size=200`,`fixedDelay=60s` | 以批次大小作为主调节旋钮，优先降批次避免 DB 抖动 |
| MQ 消费 | `prefetch=20` | 高压时降到 `10`，减少单实例堆积与长尾 |

---

## 4. 降级规则（行为可预测）

## 4.1 同步接口降级矩阵

| 接口 | 一级降级 | 二级降级 | 恢复条件 |
|---|---|---|---|
| 创建订单 | 启用严格限流（阈值减半） | 仅保留白名单流量（运营/压测开关） | 5 分钟内成功率与 RT 恢复 |
| 支付订单 | 频控 + 幂等提示优先 | 仅允许已创建订单支付，不接受边缘场景请求 | 错误率回落到阈值以下 |
| 支付回调 | 回调幂等优先，快速 ACK | 暂停非关键联动（通知类）保核心状态推进 | Outbox/消费失败告警解除 |

## 4.2 异步链路降级矩阵

| 链路 | 一级降级 | 二级降级 |
|---|---|---|
| Outbox 发布 | 降低批次，延长重试间隔 | 人工触发分批发布，暂停非关键事件类型 |
| 提醒/通知任务 | 降批次 + 延后提醒 | 关闭非关键提醒开关（保支付/退款核心链路） |
| MQ 消费 | 下调 prefetch | 临时停消费非核心队列，优先核心队列 |

---

## 5. 方案与现有代码映射

1. 热点入口：
   - `OrdersController.createOrder/pay`、`PaymentController.callback`。
2. 核心幂等保护：
   - `OrderServiceImpl.createOrder/payOrder/handlePaymentCallback`。
3. 削峰与异步：
   - `OutboxPublishJob`、`OutboxMonitorJob`；
   - `OrderShipTimeoutTaskJob`、`OrderRefundTaskJob`、`OrderShipReminderTaskJob`；
   - `OrderPaidConsumer`、`OrderStatusChangedConsumer`。
4. 参数基线：
   - `application.yml` 中 `outbox.*`、`order.*`、`spring.rabbitmq.listener.simple.prefetch`。

---

## 6. 实施优先级（落地顺序）

1. P0：网关限流规则上线（create/pay/callback）。
2. P0：应用层 `userId/orderId/orderNo` 限流与去重键。
3. P1：压测场景下 `batch-size/prefetch` 动态调优剧本。
4. P1：补齐“超限事件审计字段”并纳入 P4 告警。

---

## 7. DoD 对齐（P6-S2 文档阶段）

- [x] 热点接口均有保护策略（网关 + 应用 + 幂等 + 降级）。  
- [x] 突发流量下行为可预测（超限语义、降级矩阵、恢复条件已固化）。  
- [ ] 待后续压测回归补充实测吞吐与 RT 数据。  

---

## 8. 证据索引

1. `demo-service/src/main/java/com/demo/controller/user/OrdersController.java`
2. `demo-service/src/main/java/com/demo/controller/PaymentController.java`
3. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
4. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
5. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
6. `demo-service/src/main/java/com/demo/job/OrderShipTimeoutTaskJob.java`
7. `demo-service/src/main/java/com/demo/job/OrderRefundTaskJob.java`
8. `demo-service/src/main/java/com/demo/job/OrderShipReminderTaskJob.java`
9. `demo-service/src/main/java/com/demo/mq/consumer/OrderPaidConsumer.java`
10. `demo-service/src/main/java/com/demo/mq/consumer/OrderStatusChangedConsumer.java`
11. `demo-service/src/main/resources/application.yml`

---

（文件结束）
