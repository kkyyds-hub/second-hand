# Day17 P4-S3 并发安全改造清单 v1.0

- 日期：2026-02-24
- 目标：避免并发覆盖和状态错乱，统一 `rows=0` 并发分流语义。
- 范围：订单主链路 + 任务链路（发货超时/发货提醒/退款）。

---

## 1. 统一并发口径（冻结）

1. **CAS 更新**：关键状态流转必须使用“主键 + 期望状态”条件更新。  
2. **`rows=0` 非直接失败**：统一先回查，再分流为：
   - 幂等命中（别人已处理成功）  
   - 并发冲突（状态已被其他流程推进）  
   - 数据异常（记录不存在）  
3. **语义明确**：用户接口返回明确业务语义；任务链路记录明确并发日志。

---

## 2. 本次改造点清单

### 2.1 订单主链路（既有 CAS 复核）

| 链路 | CAS 条件 | `rows=0` 分流语义 |
|---|---|---|
| `payOrder` | `id + buyer_id + status=pending` | 已支付/后继状态 => 幂等成功；已取消 => 业务拒绝 |
| `cancelOrder` | `id + buyer_id + status=pending` | 已取消 => 幂等成功；已支付后续状态 => 业务拒绝 |
| `shipOrder` | `id + seller_id + status=paid` | 已发货/已完成 => 幂等成功；已取消 => 业务拒绝 |
| `confirmOrder` | `id + buyer_id + status=shipped` | 已完成 => 幂等成功；已取消 => 业务拒绝 |
| `handlePaymentCallback` | `order_no + status=pending` | 已支付/后继状态 => 幂等成功；已取消 => 业务拒绝 |
| `closeShipTimeoutOrder` | `id + status=paid + pay_time<=deadline` | 状态不满足时分流到取消/重试路径 |

说明：订单主链路在此前已具备 CAS，本次重点是统一任务链路的 `rows=0` 分流和日志口径。

### 2.2 退款任务链路（本次增强）

#### 变更 1：退款任务状态推进改为“精确期望状态 CAS”

- 文件：`demo-service/src/main/java/com/demo/mapper/OrderRefundTaskMapper.java`
- 文件：`demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
- 改造前：
  - `markSuccess`: `status IN ('PENDING','FAILED')`
  - `markFail`: `status IN ('PENDING','FAILED')`
- 改造后：
  - `markSuccess(id, expectedStatus)`: `status = expectedStatus`
  - `markFail(id, expectedStatus, ...)`: `status = expectedStatus`

目的：避免“旧快照线程”跨状态覆盖写，保证每次更新都基于调用方读取到的期望状态。

#### 变更 2：新增按任务 ID 回查能力

- 新增：`selectById(id)`（Mapper + XML）
- 用途：当 CAS 更新 `rows=0` 时，回查最新状态并给出明确并发语义。

### 2.3 发货提醒任务链路（本次增强）

#### 变更 1：新增 `selectById(id)` 回查

- 文件：`demo-service/src/main/java/com/demo/mapper/OrderShipReminderTaskMapper.java`
- 文件：`demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`

#### 变更 2：`OrderShipReminderTaskProcessor` 统一 `rows=0` 分流

- 文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskProcessor.java`
- 行为：
  1. `markSuccess` 返回 0 时回查：
     - 最新 `SUCCESS` => 幂等命中  
     - 其他状态 => 并发 CAS 未命中  
  2. `markFail` / `markCancelled` 返回 0 时统一回查并记录并发语义日志。

#### 变更 3：卡死回收批量更新增加行数校验

- 文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java`
- 行为：`markFailBatch` 返回行数与期望 ID 数不一致时记录并发 miss 日志。

### 2.4 发货超时任务链路（本次增强）

- 文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`
- 行为：
  1. `markDone/markCancelled/markRetry` 全部做 `rows=0` 分流日志。
  2. `rows=0` 时通过 `selectByOrderId` 回查任务最新状态，记录 `latestTaskStatus`。

---

## 3. 并发失败分支语义（本次统一）

### 3.1 用户接口语义

1. 幂等命中：返回“已处理/无需重复操作”。  
2. 非法流转：返回明确业务错误（如“订单已取消，无法支付”）。  
3. 并发冲突：通过回查状态后返回可解释语义，不返回模糊系统错误。

### 3.2 任务链路语义

1. `rows=0` 不作为系统异常抛出。  
2. 通过回查区分：  
   - 已成功（幂等）  
   - 已被其他线程推进到终态  
   - 记录缺失/数据异常  
3. 统一记录 CAS 未命中日志，便于排障。

---

## 4. 本次新增并发回归测试（代码用例）

1. `demo-service/src/test/java/com/demo/concurrency/OrderRefundTaskProcessorConcurrencyTest.java`  
   - 验证退款任务 `markSuccess rows=0` 且最新为 `SUCCESS` 时按幂等分流。  
   - 验证记账异常时按 `expectedStatus` 执行 `markFail`。  
2. `demo-service/src/test/java/com/demo/concurrency/OrderShipReminderTaskProcessorConcurrencyTest.java`  
   - 验证提醒任务 `markSuccess rows=0` 的并发分流。  
   - 验证终态订单取消任务时 CAS 未命中不抛异常。  
3. `demo-service/src/test/java/com/demo/concurrency/OrderShipTimeoutTaskProcessorConcurrencyTest.java`  
   - 验证超时任务在终态订单下 CAS 未命中分流不抛异常。

---

## 5. DoD 对齐（P4-S3）

- [x] 关键状态流转具备并发防护（CAS + 回查分流）  
- [x] 并发失败分支具备明确业务语义/日志语义  
- [x] 补充了关键链路并发回归测试用例  
- [x] 形成并发安全改造清单并可复现

---

（文件结束）
