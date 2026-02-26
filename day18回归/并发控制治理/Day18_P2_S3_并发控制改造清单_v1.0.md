# Day18 P2-S3 并发控制改造清单 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P2-S3：并发控制策略统一`
- 目标：统一关键状态迁移的 CAS 条件与 `rows=0` 分流语义，保证并发行为可预测。

---

## 1. 统一口径（P2-S3 冻结）

1. 关键状态迁移统一采用条件更新（CAS）：`id + expectedStatus`（或等价的 `currentStatus` 条件）。
2. `rows=0` 必须做二次回查，统一分流为三类：
   - 已到目标终态：按幂等成功返回；
   - 被并发改为其他状态：返回稳定并发提示；
   - 非法状态：返回业务错误。
3. 悲观锁仅限强一致资金链路（如钱包记账），非资金状态机优先 CAS。

---

## 2. 关键链路基线与收口结果

| 领域 | 关键更新点 | CAS 条件 | rows=0 语义 | 结论 |
|---|---|---|---|---|
| 订单主链路 | `updateForPay/updateForCancel/updateForShipping/updateForConfirm/updateForPayByOrderNo/closeShipTimeoutOrder` | `id(or order_no)+status(+buyer/seller)` | Service 统一做回查分流（幂等/非法） | 已达标 |
| 商品状态机 | `updateStatusAndReasonByCurrentStatus` / `updateStatusAndReasonByOwnerAndCurrentStatus` | `id(+owner)+current_status` | `transit()` 统一处理幂等与并发 | 已达标 |
| 举报工单 | `resolveIfPending` | `ticketNo + status=PENDING` | rows=0 回查后返回“已处理”或失败 | 已达标 |
| 任务-发货超时 | `markDone/markCancelled/markRetry` | `id + status=PENDING` | 记录 CAS 未命中并回查最新状态 | 已达标 |
| 任务-退款 | `markSuccess/markFail` | `id + expectedStatus` | rows=0 幂等命中或并发告警 | 已达标 |
| 任务-发货提醒 | `markSuccess/markFail/markCancelled` | `id + status=RUNNING` | rows=0 回查最新状态 | 已达标 |
| Outbox 批量回写 | `markSentBatch/markFailBatch` | `id + status in (NEW,FAIL)` | 回写行数漂移记录日志 | 已达标 |
| 用户状态流转 | `updateStatusByExpected`（本次新增） | `id + expectedStatus` | rows=0 回查：幂等或并发失败 | 本次已收口 |

---

## 3. 本次代码改造（P2-S3）

## 3.1 新增 CAS Mapper 能力

1. `UserMapper` 新增：
   - `updateStatusByExpected(userId, expectedStatus, targetStatus)`
2. `UserMapper.xml` 新增：
   - `UPDATE users ... WHERE id = #{userId} AND status = #{expectedStatus}`

## 3.2 用户状态迁移统一到 CAS + rows=0 分流

1. `UserServiceImpl`
   - `banUser/unbanUser` 改为 `updateStatusByExpected`
   - `rows=0` 时回查最新状态，统一返回：
     - 幂等命中：`用户已处于封禁状态/正常状态`
     - 并发变更：`用户状态已变化，请刷新后重试`
2. `ViolationServiceImpl`
   - `banUser/unbanUser` 改为 CAS 更新
   - `rows=0` 回查并发分流，避免状态覆盖写
3. `AuthServiceImpl`
   - `activateEmail` 改为 `inactive -> active` CAS
   - 登录风控冻结改为 `active -> frozen` CAS，避免覆盖并发状态

---

## 4. 悲观锁引入评估结论

1. 保留悲观锁场景：
   - 钱包记账链路 `selectByUserIdForUpdate`（`WalletMapper.xml`）
2. 不新增悲观锁场景：
   - 订单/商品/任务状态机均已具备 CAS 条件更新和幂等分流；
   - 引入悲观锁会增加锁等待与吞吐风险，当前无必要。

---

## 5. 风险与后续项

1. `MessageOutboxMapper.markSent/markFail` 单条更新为通用能力，当前主链路使用批量 CAS 回写；建议后续统一收敛到批量回写接口。
2. `MqConsumeLogMapper.updateStatus` 属消费日志写回，当前不承载业务状态机；若后续扩展多状态流转，可补 `expectedStatus` 约束。

---

## 6. DoD 对齐（当前状态）

- [x] 关键状态迁移已形成统一 CAS 口径（含用户状态链路补齐）。  
- [x] `rows=0` 并发分流语义已在关键链路统一。  
- [x] 悲观锁仅保留必要资金链路并完成评估说明。  
- [x] 已完成动态并发验证并回填执行记录。  

---

## 7. 代码证据索引

1. `demo-service/src/main/java/com/demo/mapper/UserMapper.java`
2. `demo-service/src/main/resources/mapper/UserMapper.xml`
3. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
4. `demo-service/src/main/java/com/demo/service/serviceimpl/ViolationServiceImpl.java`
5. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
6. `demo-service/src/main/resources/mapper/OrderMapper.xml`
7. `demo-service/src/main/resources/mapper/ProductMapper.xml`
8. `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
9. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
10. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
11. `demo-service/src/main/resources/mapper/WalletMapper.xml`

---

（文件结束）
