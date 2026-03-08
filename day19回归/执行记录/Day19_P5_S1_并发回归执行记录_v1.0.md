# Day19 P5-S1 并发回归执行记录 v1.0

- 日期：2026-03-08
- 关联冻结文档：`day19回归/Day19_Scope_Freeze_v1.0.md`
- 关联复现脚本：`day19回归/执行复现步骤/Day19_P5_S1_并发回归_执行复现_v1.0.ps1`
- 当前状态：已完成（修复后复跑通过）
- 最终执行证据：`day19回归/执行记录/Day19_P5_S1_动态结果_2026-03-08_16-55-27.json`

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. 执行人：`Codex`
3. 执行时间：`2026-03-08 16:55:27 ~ 2026-03-08 16:55:38`
4. 并发规模：`50`
5. 运行标识：`DAY19-P5-S1-20260308165527`
6. 总耗时：`10875.2 ms`

---

## 2. 本轮收口说明

1. 支付回调链路在 CAS 未命中后的复查阶段改为 `READ_COMMITTED` 口径，避免并发下读取到事务旧快照，重复回调统一收敛为固定幂等返回。
2. 用户封禁/解封链路在方法级隔离下复查最新状态，重复请求收敛为“成功 1 次 + 幂等 49 次”，不再漂移到 `CAS_CONFLICT`。
3. `InventoryUpdateConsumer` 增加“订单已取消则跳过回写 SOLD”保护，修复发货超时关单后商品被旧 `ORDER_CREATED` 事件错误回写为 `sold` 的问题。
4. 支付回调幂等文案统一为 `订单已支付，回调幂等成功`，避免同场景下返回口径漂移。

---

## 3. 动态执行回填表

| 场景 | 并发 | 实际分流 | 冲突比例 | 最终一致性结果 | 是否通过 |
|---|---:|---|---:|---|---|
| S1 同订单重复支付 | 50 | `支付成功 x1`；`订单已支付，无需重复操作 x49` | `0%` | 订单 `paid`；发货超时任务 `1` 条且 `PENDING`；`ORDER_CREATED` Outbox `1` 条；未产生退款任务 | `[x]` |
| S2 同订单重复成功回调 | 50 | `支付回调处理成功 x1`；`订单已支付，回调幂等成功 x49` | `0%` | 订单 `paid`；发货超时任务 `1` 条且 `PENDING`；`ORDER_PAID` Outbox `1` 条；`ORDER_CREATED` Outbox `1` 条 | `[x]` |
| S3 同用户重复封禁 | 50 | `用户封禁成功 x1`；`用户已处于封禁状态 x49` | `0%` | 用户最终状态 `banned`；无重复封禁副作用 | `[x]` |
| S4 同用户重复解封 | 50 | `用户解封成功 x1`；`用户已处于正常状态 x49` | `0%` | 用户最终状态 `active`；无重复解封副作用 | `[x]` |
| S5 发货超时任务并发抢同一条任务 | 50 | `success x50` | `0%` | 订单 `cancelled`，原因为 `ship_timeout`；任务 `DONE`；退款任务 `1` 条且 `PENDING`；商品最终回到 `on_sale` | `[x]` |

---

## 4. 关键指标摘录

1. S1 `P95=2059.12ms`，支付主成功 `1/50`，其余 `49/50` 全部收敛为幂等成功。
2. S2 `P95=1677.01ms`，重复回调 `49/50` 全部收敛为统一幂等文案，无 `pending` 漂移失败。
3. S3 `P95=745.73ms`，S4 `P95=765.61ms`，封禁/解封并发冲突均收敛为 `0%` 失败。
4. S5 `P95=1394.38ms`，任务并发抢占未产生重复关单、重复退款或商品状态反向污染。

---

## 5. DoD 勾选（回填区）

- [x] 并发冲突不会导致重复业务副作用。
- [x] 冲突分流文案与日志口径稳定。
- [x] 冲突比例与最终一致性结果已完成归档。
- [x] 执行证据已归档到 `day19回归/执行记录/Day19_P5_S1_动态结果_2026-03-08_16-55-27.json`。

---

## 6. 涉及修复点

1. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
2. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
3. `demo-service/src/main/java/com/demo/mq/consumer/InventoryUpdateConsumer.java`
4. `demo-service/src/test/java/com/demo/concurrency/InventoryUpdateConsumerConcurrencyTest.java`

---

（文件结束）
