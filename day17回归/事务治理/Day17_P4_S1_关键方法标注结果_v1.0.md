# Day17 P4-S1 关键方法标注结果 v1.0

- 日期：2026-02-24
- 目标：将关键事务边界从“约定”升级为“代码显式标注”

---

## 1. 本次新增/调整的事务标注

| 文件 | 方法 | 标注 | 目的 |
|---|---|---|---|
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `buy` / `getSellOrder` / `getOrderDetail` | `@Transactional(readOnly = true)` | 明确读链路口径，降低误写风险 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `shipOrder` | `@Transactional(rollbackFor = Exception.class)` | 显式写事务，统一订单写链路风格 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OutboxServiceImpl.java` | `save` | `@Transactional(MANDATORY, rollbackFor = Exception.class)` | 强制 Outbox 必须在业务事务内落库 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/PointsServiceImpl.java` | `grantPointsForOrderComplete` | `@Transactional(MANDATORY, rollbackFor = Exception.class)` | 强制积分发放在订单事务内执行 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutPenaltyServiceImpl.java` | `applyPenalty` | `@Transactional(MANDATORY, rollbackFor = Exception.class)` | 强制处罚动作在超时关单事务内执行 |

---

## 2. 已有且保持不变的关键事务入口

| 文件 | 方法 | 当前标注 | 说明 |
|---|---|---|---|
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `createOrder` | `@Transactional(rollbackFor = Exception.class)` | 下单主交易入口 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `payOrder` | `@Transactional(rollbackFor = Exception.class)` | 支付主交易入口 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `cancelOrder` | `@Transactional(rollbackFor = Exception.class)` | 取消主交易入口 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `confirmOrder` | `@Transactional(rollbackFor = Exception.class)` | 确认收货主交易入口 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` | `handlePaymentCallback` | `@Transactional(rollbackFor = Exception.class)` | 支付回调主交易入口 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java` | `processOne` | `@Transactional(rollbackFor = Exception.class)` | 超时关单单条事务 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java` | `processOne` | `@Transactional(rollbackFor = Exception.class)` | 退款处理单条事务 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OutboxBatchStatusService.java` | `flushPublishResult` | `@Transactional(rollbackFor = Exception.class)` | Outbox 状态批量回写事务 |

---

## 3. 明确不加事务的方法（设计性保留）

| 文件 | 方法 | 保留理由 |
|---|---|---|
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutServiceImpl.java` | `processDueTasks` | 调度层不包整批事务，避免长事务与大范围回滚 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskServiceImpl.java` | `processRunnableTasks` | 调度层只负责任务分发，单条事务下沉到 Processor |

---

## 4. 口径结论

1. 订单主链路与超时退款链路的关键跨表写入均有明确事务保护。  
2. 读链路与写链路边界已显式分离（readOnly vs rollbackFor）。  
3. Outbox/积分/处罚关键点改为 `MANDATORY` 后，可在运行时拦截“脱离事务调用”。

---

（文件结束）
