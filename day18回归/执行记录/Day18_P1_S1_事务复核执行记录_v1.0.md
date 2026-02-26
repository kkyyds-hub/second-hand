# Day18 P1-S1 事务复核执行记录 v1.0

- 日期：2026-02-25  
- 范围：事务边界静态复核 + 动态故障注入验证（A/B/C）。  
- 关联复现文档：`day18回归/执行复现步骤/Day18_P1_S1_事务回滚与通知顺序_执行复现_v1.0.md`

---

## 1. 本次执行命令（静态复核）

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
rg -n "@Transactional|Propagation|rollbackFor|readOnly|afterCommit|TransactionSynchronizationManager" demo-service\src\main\java -S --glob "*.java"
rg -n "@Transactional\(readOnly = true\)|@Transactional\(rollbackFor = Exception.class\)|afterCommit|outboxService.save" demo-service\src\main\java\com\demo\service\serviceimpl\OrderServiceImpl.java -S
rg -n "@Transactional\(rollbackFor = Exception.class\)|registerAfterCommit|markRetry|markDone|markCancelled|markFail" demo-service\src\main\java\com\demo\service\serviceimpl\OrderShipTimeoutTaskProcessor.java demo-service\src\main\java\com\demo\service\serviceimpl\OrderRefundTaskProcessor.java -S
rg -n "Propagation\.MANDATORY" demo-service\src\main\java\com\demo\service\serviceimpl\OutboxServiceImpl.java demo-service\src\main\java\com\demo\service\serviceimpl\PointsServiceImpl.java demo-service\src\main\java\com\demo\service\serviceimpl\OrderShipTimeoutPenaltyServiceImpl.java -S
```

---

## 2. 静态复核结果摘要

1. `OrderServiceImpl` 存在 3 处 `readOnly=true` 查询事务。  
2. `OrderServiceImpl` 关键写方法均标注 `rollbackFor = Exception.class`。  
3. `OrderServiceImpl` 存在 `safePublish -> afterCommit` 事务后置发送逻辑。  
4. `OrderShipTimeoutTaskProcessor`、`OrderRefundTaskProcessor` 单条处理均为事务方法，并具备 `afterCommit` 通知。  
5. `OutboxServiceImpl`、`PointsServiceImpl`、`OrderShipTimeoutPenaltyServiceImpl` 均为 `Propagation.MANDATORY`。  
6. 调度层（Job/TaskService）采用“无事务编排 + 单条事务处理器”模式，符合收口口径。

---

## 3. 动态验证结果（A/B/C）

### 3.1 场景 A：`createOrder` 回滚验证

1. 使用 `shippingAddress=day18_tx_probe_create_order` 触发事务内探针异常。  
2. 结果：接口返回失败（`code=0`）。  
3. 数据校验：
   - 目标商品 `productId=920002` 状态 `on_sale -> on_sale`（无错误残留）；
   - `orders` 中该 `shippingAddress` 记录数：`0 -> 0`；
   - `order_items`（join 该 `shippingAddress`）记录数：`0 -> 0`；
   - `message_outbox`（join 该 `shippingAddress` 订单）记录数：`0 -> 0`。
4. 结论：主事务整体回滚生效。

### 3.2 场景 B：`afterCommit` 顺序验证（超时任务）

1. 构造订单并支付后，定位任务：`orderId=900041`，`taskId=19`。  
2. 将任务调为可执行并设置探针标记（`last_error=day18_tx_probe_after_commit`），触发 `ship-timeout run-once`。  
3. 结果：`run-once` 返回成功（框架层），但业务单条处理回滚。  
4. 数据校验：
   - 订单状态：`paid -> paid`（未提交到 `cancelled`）；
   - 任务状态：`PENDING -> PENDING`（未提交到 `DONE`）；
   - `order_refund_task` 该订单记录数：`0 -> 0`。
5. 结论：事务未提交时，`afterCommit` 路径未生效，顺序符合预期。

### 3.3 场景 C：`MANDATORY` 约束验证（Outbox）

1. 调用无事务探针入口，直接触发 `OutboxService.save(...)`。  
2. 结果：接口返回失败（`code=0, msg=服务器错误`）。  
3. 数据校验：`message_outbox` 中 `event_type='DAY18_PROBE'` 记录数 `0 -> 0`。  
4. 结论：`Propagation.MANDATORY` 约束生效，脱离事务调用失败且未落库。

---

## 4. 探针清理记录（必须）

1. 已清理 `OrderServiceImpl` 的 `day18_tx_probe_create_order` 临时注入。  
2. 已清理 `OrderShipTimeoutTaskProcessor` 的 `day18_tx_probe_after_commit` 临时注入。  
3. 已清理 `AdminTaskOpsController` 的 `probe/outbox-mandatory` 临时探针接口。  
4. 代码中检索 `day18_tx_probe|probe/outbox-mandatory|DAY18_PROBE` 无命中。

---

## 5. DoD 勾选（P1-S1）

- [x] 关键事务异常路径可整体回滚。  
- [x] `afterCommit` 执行顺序验证通过。  
- [x] `MANDATORY` 事务约束验证通过。  
- [x] 所有临时注入代码已清理。  

---

## 6. 结论

1. P1-S1 静态与动态验证均通过。  
2. 回滚、通知顺序、`MANDATORY` 约束具备可复现执行证据。  
3. 可继续进入后续步骤（P1-S2/P1-S3）。

---

（文件结束）
