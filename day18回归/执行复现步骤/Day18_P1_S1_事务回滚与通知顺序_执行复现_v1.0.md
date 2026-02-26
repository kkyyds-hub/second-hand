# Day18 P1-S1 事务回滚与通知顺序 执行复现 v1.0

- 日期：2026-02-25  
- 目标：验证关键链路“事务回滚有效”与“afterCommit 顺序正确”。  
- 说明：本复现包含临时故障注入，执行完必须回退注入代码。

---

## 1. 复现前准备

1. 使用独立测试库，避免污染回归数据。  
2. 打开 SQL 日志：`logging.level.com.demo.mapper=debug`。  
3. 打开服务日志：`logging.level.com.demo.service=info`。  
4. 备份本地代码改动，确保故障注入可安全回退。

---

## 2. 场景 A：`createOrder` 回滚验证

### 2.1 临时注入点

文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`  
在 `outboxService.save(outbox);` 后临时加入：

```java
throw new RuntimeException("day18_tx_probe_create_order");
```

### 2.2 执行

1. 调用“下单”接口一次。  
2. 预期返回失败（统一异常响应）。

### 2.3 校验

按本次 `orderNo` / `productId` 校验：

1. `orders` 无新增。  
2. `order_items` 无新增。  
3. `message_outbox` 无对应 `biz_id` 记录。  
4. `products` 不应残留为错误状态。  

结论：主事务内写入整体回滚。

---

## 3. 场景 B：`afterCommit` 通知顺序验证（超时任务）

### 3.1 临时注入点

文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`  
在 `registerAfterCommit(...)` 之后临时加入：

```java
throw new RuntimeException("day18_tx_probe_after_commit");
```

### 3.2 执行

1. 构造一条可执行 `order_ship_timeout_task`。  
2. 触发 `OrderShipTimeoutTaskJob` 一轮。

### 3.3 校验

1. 订单状态与任务状态未持久化到成功终态（事务已回滚）。  
2. 未出现“超时取消通知发送成功”结果。  
3. 日志中不应出现该次事务对应的 afterCommit 成功发送日志。  

结论：事务未提交时，afterCommit 不执行。

---

## 4. 场景 C：`MANDATORY` 约束验证（Outbox）

### 4.1 验证目标

`OutboxServiceImpl.save` 使用 `Propagation.MANDATORY`，脱离事务直接调用必须失败。

### 4.2 验证方式

1. 在本地临时写一个无事务调用入口直接调用 `outboxService.save(...)`。  
2. 预期抛出事务状态异常（如 `IllegalTransactionStateException`）。

结论：Outbox 只能在主事务中落库。

---

## 5. 场景 D：静态口径复核（无需注入）

在项目根目录执行：

```powershell
rg -n "@Transactional\(readOnly = true\)|@Transactional\(rollbackFor = Exception.class\)|afterCommit" demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java -S
rg -n "Propagation.MANDATORY" demo-service/src/main/java/com/demo/service/serviceimpl/OutboxServiceImpl.java demo-service/src/main/java/com/demo/service/serviceimpl/PointsServiceImpl.java demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutPenaltyServiceImpl.java -S
```

预期：

1. 查询方法使用 `readOnly=true`。  
2. 核心写方法使用 `rollbackFor=Exception.class`。  
3. `afterCommit` 与 `MANDATORY` 证据可直接定位。

---

## 6. 回退动作（必须）

1. 删除所有临时 `throw` 注入代码。  
2. 重新编译并跑一轮关键回归。  
3. 确认仓库不残留故障注入改动。

---

## 7. DoD 验收勾选

- [ ] 关键事务异常路径可整体回滚。  
- [ ] `afterCommit` 执行顺序验证通过。  
- [ ] `MANDATORY` 事务约束验证通过。  
- [ ] 所有临时注入代码已清理。

---

（文件结束）
