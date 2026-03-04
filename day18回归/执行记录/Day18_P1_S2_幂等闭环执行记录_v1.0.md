# Day18 P1-S2 幂等闭环执行记录 v1.0

- 日期：2026-02-25
- 范围：幂等策略静态复核 + 测试库唯一约束核验（`secondhand2`）。
- 关联复现文档：`day18回归/执行复现步骤/Day18_P1_S2_幂等命中与唯一约束闭环_执行复现_v1.0.md`

---

## 1. 本次执行命令（已执行）

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'

rg -n "payOrder|handlePaymentCallback|createShipTimeoutTaskIfAbsent|logIdempotencyHit|订单已支付" `
  demo-service\src\main\java\com\demo\service\serviceimpl\OrderServiceImpl.java -S

rg -n "insertIgnore|markSuccess|markFail|幂等命中" `
  demo-service\src\main\java\com\demo\service\serviceimpl\OrderShipReminderTaskServiceImpl.java `
  demo-service\src\main\java\com\demo\service\serviceimpl\OrderShipTimeoutTaskProcessor.java `
  demo-service\src\main\java\com\demo\service\serviceimpl\OrderRefundTaskProcessor.java `
  demo-service\src\main\resources\mapper\OrderShipReminderTaskMapper.xml `
  demo-service\src\main\resources\mapper\OrderShipTimeoutTaskMapper.xml `
  demo-service\src\main\resources\mapper\OrderRefundTaskMapper.xml -S

rg -n "DuplicateKeyException|幂等命中：consumer=|eventId" `
  demo-service\src\main\java\com\demo\mq\consumer -S

& 'D:\mysql\mysql-8.4.6-winx64\bin\mysql.exe' -uroot -p1234 -D secondhand2 -e "<唯一约束核对 SQL>"
& 'D:\mysql\mysql-8.4.6-winx64\bin\mysql.exe' -uroot -p1234 -D secondhand2 -e "<重复数据扫描 SQL>"
```

---

## 2. 动态验证执行结果（2026-02-25 + 2026-03-04补跑）

### 2.1 场景 A：重复支付

1. 创建订单：`orderId=900038`，`orderNo=2026022510530914061`。  
2. 第一次支付返回：`支付成功`。  
3. 第二次支付返回：`订单已支付，无需重复操作`。  
4. 数据校验：`order_ship_timeout_task` 中该订单任务数为 `1`。

### 2.2 场景 B：重复支付回调

1. 创建订单：`orderId=900039`，`orderNo=2026022510533011735`。  
2. 第一次回调返回：`支付回调处理成功`。  
3. 第二次回调返回：`订单已支付，回调幂等成功!`。  
4. 数据校验：订单状态为 `paid`，且 `order_ship_timeout_task` 该订单任务数为 `1`。

### 2.3 场景 C：任务 run-once 重复触发

1. 连续两次触发 `ship-timeout run-once`，接口返回 `code=1`。  
2. 连续两次触发 `refund run-once`，接口返回 `code=1`。  
3. 重复扫描结果：  
   - `order_ship_timeout_task(order_id)`：0  
   - `order_ship_reminder_task(order_id,level)`：0  
   - `order_refund_task(order_id,refund_type)`：0  
   - `order_refund_task(idempotency_key)`：0  
   - `mq_consume_log(consumer,event_id)`：0  
   - `points_ledger(user,biz_type,biz_id)`：0

### 2.4 2026-03-04 补充证据（运行态日志可检索）

1. 新增证据文件：`day18回归/执行记录/Day18_CloseLoop_Dynamic_Result_2026-03-04_10-53-17.json`。  
2. 幂等命中日志样本已补齐（`_tmp_day18_app18080.out.log`）：  
   - `幂等命中：action=payOrder, idemKey=orderId:900065, detail=status=paid`  
   - `幂等命中：action=createShipTimeoutTask, idemKey=orderId:900065, detail=scene=payOrder:idempotent_paid`  
3. 审计样本可对应同一业务链路：`ORDER_PAY result=SUCCESS/IDEMPOTENT` 均可检索。

---

## 3. 静态核对结果摘要

1. `OrderServiceImpl` 已形成统一幂等分流：`payOrder`、`handlePaymentCallback`、`shipOrder`、`confirmOrder`、`cancelOrder`。  
2. 任务链路已使用 `insertIgnore` + CAS：发货超时任务、提醒任务、退款任务。  
3. MQ 消费端已使用 `consumer + eventId` 抢占去重，并在冲突时记录幂等命中。  
4. 测试库关键唯一索引（`uk_orders_order_no`、`uk_event_id`、`uk_consumer_event` 等）均存在。

---

## 4. 结论

1. P1-S2 已完成动态幂等验证（重复支付、重复回调、重复任务触发）且结果符合预期。  
2. 关键唯一键未出现重复数据组，说明“幂等键 + 唯一约束/CAS”闭环有效。  
3. `幂等命中` 运行日志样本已在 2026-03-04 补采，P1-S2 DoD 全部达成。

---

## 5. DoD 勾选（本次）

- [x] 重复请求不会写出重复业务记录。  
- [x] 幂等命中日志可检索、可追踪（已补充运行态日志样本与证据文件）。  
- [x] 唯一约束与 `insertIgnore`/CAS 语义一致性核对完成。  
- [x] 已输出幂等策略清单 v2 与唯一约束核对结果。  

---

（文件结束）
