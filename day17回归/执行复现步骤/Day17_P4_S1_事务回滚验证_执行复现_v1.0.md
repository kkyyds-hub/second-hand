# Day17 Step P4-S1：事务回滚验证执行复现 v1.0

- 日期：2026-02-24
- 目标：验证“订单主链路 + 超时退款链路”的异常路径回滚行为
- 说明：本步骤使用**本地临时故障注入**（调试分支），验证完成后需回退临时代码

---

## 1. 前置条件

1. 使用独立测试库（建议 `secondhand2_test`）。  
2. 服务可启动，`demo-service` 编译通过。  
3. 打开 SQL 日志：`com.demo.mapper=debug`。  

---

## 2. 场景 A：`createOrder` 事务回滚验证

### 2.1 注入点（临时）

在 `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` 的 `createOrder` 中，`outboxService.save(outbox);` 后临时加入：

```java
throw new RuntimeException("tx_rollback_probe_create_order");
```

### 2.2 执行

1. 调用“用户下单”接口一次。  
2. 预期接口返回 500（或统一异常响应）。  

### 2.3 校验 SQL

针对本次 `orderNo` / `productId` 校验：

1. `orders` 无新增记录；  
2. `order_items` 无新增记录；  
3. `message_outbox` 无对应 `biz_id` 记录；  
4. `products` 状态未永久停留在 `sold`（应回滚到原状态）。  

---

## 3. 场景 B：超时关单任务回滚验证

### 3.1 注入点（临时）

在 `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java` 的 `createRefundTaskIfAbsent(order);` 之后、`taskMapper.markDone(taskId);` 之前临时加入：

```java
throw new RuntimeException("tx_rollback_probe_ship_timeout");
```

### 3.2 执行

1. 构造一条可执行的 `order_ship_timeout_task`（状态 `PENDING`、到期）。  
2. 触发 `OrderShipTimeoutTaskJob` 一轮。  

### 3.3 校验 SQL

针对该 `orderId` 校验：

1. `orders.status` 未持久化为 `cancelled`（应回滚为原状态）；  
2. `order_refund_task` 未新增该订单退款任务；  
3. `order_ship_timeout_task` 未被推进到 `DONE`；  
4. 若开启处罚，`user_violations` 与信用相关写入也应回滚。  

---

## 4. 场景 C：`MANDATORY` 事务约束验证（Outbox）

### 4.1 验证目标

`OutboxServiceImpl.save` 标注为 `Propagation.MANDATORY`，脱离事务调用应抛异常。

### 4.2 方式

在本地临时写一个无事务调用点（或单元测试）直接调用 `outboxService.save(...)`。  
预期抛出 `IllegalTransactionStateException`（或同类事务状态异常）。

---

## 5. 回退动作（必须）

1. 删除上述两处临时 `throw` 注入代码；  
2. 重新编译并回归关键接口；  
3. 确认代码库不残留调试注入。  

---

## 6. DoD 勾选

- [ ] `createOrder` 异常可整体回滚  
- [ ] 超时关单链路异常可整体回滚  
- [ ] `MANDATORY` 约束可拦截脱离事务调用  
- [ ] 调试注入代码已清理  

---

（文件结束）
