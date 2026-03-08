# Day19 P4-S2 任务链路性能与恢复报告 v1.0

- 日期：`2026-03-08`
- 对应范围：`day19回归/Day19_Scope_Freeze_v1.0.md` -> `Phase 4 / Step P4-S2`
- 当前状态：`已完成（执行于 2026-03-08 15:30:35）`
- 动态证据：`day19回归/执行记录/Day19_P4_S2_动态结果_2026-03-08_15-27-35.json`
- 补充吞吐证据：
  - `day19回归/执行记录/Day19_P4_S2_动态结果_2026-03-08_15-39-35.json`（`limit=150`）
  - `day19回归/执行记录/Day19_P4_S2_动态结果_2026-03-08_15-38-33.json`（`limit=200`）
- 执行脚本：`day19回归/执行复现步骤/Day19_P4_S2_任务链路性能_执行复现_v1.0.ps1`

---

## 1. 目标与口径

本步骤验证三类任务链路在 `run-once` 压力下的稳定性，并补齐失败恢复闭环：

1. `ship-timeout / refund / ship-reminder` 各自做多轮 `run-once`
2. 统计每轮 `success / elapsedMs / 吞吐(task/s)`
3. 验证 `trigger-now / reset` 的手工补偿路径
4. 补一条 `ship-reminder RUNNING -> FAILED -> 自动恢复` 样本
5. 输出任务处理容量建议与重试间隔建议

统一统计口径：

1. `run-once 吞吐 = successCount / elapsedSec`
2. `successCount` 以测试批次任务在 DB 中推进到终态的数量为准，并与接口返回 `success` 交叉校验
3. `steady capacity / min` 以当前 `fixed-delay=60000ms` 估算，即 `推荐 batch-size ≈ 单 job 每分钟稳态处理量`
4. `恢复耗时`：
   - 手工补偿：从执行 `trigger-now/reset` 前开始计时，到补偿后的下一次 `run-once` 完成且任务进入终态
   - 自动恢复：从首次触发回收开始计时，到等待重试窗口后再次 `run-once` 成功

---

## 2. 执行环境与参数冻结

执行环境：

1. 服务地址：`http://localhost:8080`
2. 管理员账号：`13900000001 / admin123`
3. MySQL：`localhost:3306 / secondhand2`
4. 运行前基线：三类 `runnable` 样本均为 `0`

本轮冻结参数：

1. `BatchSizes = 10 / 25 / 50 / 100`
2. 每个 `limit` 轮次：`3`
3. 手工补偿样本：每类任务 `1` 条
4. 自动恢复样本：`ship-reminder stale RUNNING` `1` 条
5. 重试相关现状：
   - `ship-timeout.retry-delay-seconds = 120`
   - `refund.retry-delay-seconds = 120`
   - `ship-reminder` 失败退避：`2m / 5m / 15m / 30m`
   - `ship-reminder.running-timeout-minutes = 5`

补充吞吐验证：

1. 在完整闭环跑完后，额外补跑 `limit=150 / 200`
2. 补跑命令使用 `SkipAutoRecoveryWait`，只扩展吞吐对比，不替代原始恢复闭环证据

---

## 3. run-once 吞吐结果

### 3.1 汇总表

| 任务类型 | limit | 平均吞吐(task/s) | P50 吞吐(task/s) | P95 吞吐(task/s) | 平均耗时(ms) | 总 success | 总失败 |
|---|---:|---:|---:|---:|---:|---:|---:|
| ship-timeout | 10 | 23.55 | 24.15 | 24.85 | 426.03 | 30 | 0 |
| ship-timeout | 25 | 22.68 | 23.39 | 26.66 | 1132.31 | 75 | 0 |
| ship-timeout | 50 | 24.19 | 24.04 | 24.55 | 2066.89 | 150 | 0 |
| ship-timeout | 100 | 26.63 | 26.75 | 28.56 | 3769.61 | 300 | 0 |
| ship-timeout | 150 | 24.35 | 24.40 | 24.78 | 6162.05 | 450 | 0 |
| ship-timeout | 200 | 24.81 | 26.46 | 26.67 | 8149.87 | 600 | 0 |
| refund | 10 | 50.59 | 49.36 | 53.60 | 197.99 | 30 | 0 |
| refund | 25 | 57.94 | 57.84 | 58.37 | 431.54 | 75 | 0 |
| refund | 50 | 58.19 | 57.24 | 61.85 | 861.04 | 150 | 0 |
| refund | 100 | 59.51 | 59.47 | 64.86 | 1689.51 | 300 | 0 |
| refund | 150 | 50.09 | 51.71 | 55.70 | 3031.31 | 450 | 0 |
| refund | 200 | 49.72 | 49.96 | 50.95 | 4024.95 | 600 | 0 |
| ship-reminder | 10 | 71.22 | 72.03 | 74.35 | 140.66 | 30 | 0 |
| ship-reminder | 25 | 79.20 | 79.81 | 82.43 | 316.07 | 75 | 0 |
| ship-reminder | 50 | 96.06 | 101.62 | 103.71 | 525.87 | 150 | 0 |
| ship-reminder | 100 | 93.22 | 98.14 | 99.86 | 1081.63 | 300 | 0 |
| ship-reminder | 150 | 89.12 | 88.16 | 104.64 | 1715.53 | 450 | 0 |
| ship-reminder | 200 | 86.08 | 81.53 | 103.88 | 2374.72 | 600 | 0 |

### 3.2 结果解读

1. `ship-timeout`
   - `10 -> 200` 全部零失败
   - 吞吐总体平稳上升，`limit=100` 达到本轮最好平均吞吐 `26.63 task/s`
   - 补跑 `150 / 200` 后吞吐未继续提升，反而回落到 `24.35 / 24.81 task/s`
   - `limit=100` 的 `P95 elapsed = 4069.58ms`，仍是更优拐点
2. `refund`
   - `10 -> 200` 全部零失败
   - `limit=100` 最优，平均吞吐 `59.51 task/s`
   - 补跑 `150 / 200` 后平均吞吐回落到 `50.09 / 49.72 task/s`
   - `P95 elapsed = 1845.23ms`，对 60s 定时窗口仍然非常宽松
3. `ship-reminder`
   - `10 -> 200` 全部零失败
   - `limit=50` 平均吞吐最高 `96.06 task/s`
   - 补跑 `150 / 200` 后平均吞吐进一步回落到 `89.12 / 86.08 task/s`
   - 说明提醒链路在本地环境下 `50` 仍是更均衡的批量点

---

## 4. 失败恢复验证

### 4.1 手工补偿闭环

| 任务类型 | 失败样本形态 | 自动重试等待 | 补偿动作 | updatedRows | 补偿后终态 | 恢复耗时(ms) |
|---|---|---:|---|---:|---|---:|
| ship-timeout | 订单状态非法，任务仍为 `PENDING`，`last_error=invalid_order_status` | 120.23 | `trigger-now` | 1 | `DONE` | 155.52 |
| refund | 金额非法，任务进入 `FAILED`，`fail_reason=mock_refund_error:invalid refund accounting input...` | 119.51 | `reset` | 1 | `SUCCESS` | 147.42 |
| ship-reminder | 订单状态非法，任务进入 `FAILED`，`last_error=invalid_order_status` | 119.85 | `trigger-now` | 1 | `SUCCESS` | 159.39 |

结论：

1. 三类补偿入口都返回 `updatedRows=1`
2. 手工补偿实际恢复耗时都在 `0.2s` 内完成
3. 相比等待默认重试窗口（约 `120s`），手工补偿可直接节省接近整段等待时间

### 4.2 自动恢复闭环（ship-reminder stale RUNNING）

场景：人为插入 `RUNNING` 且 `running_at <= now-10m` 的提醒任务，验证 `recycleStaleRunningTasks()` 是否可回收并在下一个重试窗口恢复。

| 场景 | 首轮结果 | 回收后状态 | 等待窗口 | 再次 run-once | 最终状态 | 总恢复耗时 |
|---|---|---|---:|---|---|---:|
| stale RUNNING 回收 | `apiSuccess=0` | `FAILED`，`last_error=running_timeout_recycle` | 122.18s | 成功 | `SUCCESS` | 122598.63ms |

结论：

1. `RUNNING -> FAILED` 的回收路径可稳定触发
2. 回收后任务按首档退避进入约 `2m` 的自动恢复窗口
3. 到达重试窗口后再次 `run-once` 可恢复为 `SUCCESS`

---

## 5. 容量建议

当前三个 job 的定时轮询间隔均为 `60000ms`，因此单节点稳态处理量更受 `batch-size` 影响，而不是本次观测到的瞬时 `run-once throughput`。

| 任务类型 | 推荐 batch-size | 平均 run-once 吞吐(task/s) | P95 耗时(ms) | 单 job 稳态容量建议 | 说明 |
|---|---:|---:|---:|---:|---|
| ship-timeout | 100 | 26.63 | 4069.58 | 约 100 条/分钟 | 本轮最高吞吐且零失败；4.1s 内完成，离 60s 窗口很远 |
| refund | 100 | 59.51 | 1845.23 | 约 100 条/分钟 | 零失败且延迟余量最大，可维持 100/min 的保守稳态 |
| ship-reminder | 50 | 96.06 | 603.45 | 约 50 条/分钟 | `100` 也稳定，但吞吐略回落；`50` 更稳、波动更小 |

补充说明：

1. 已补跑 `150 / 200`，三类任务仍保持零失败
2. 但 `ship-timeout / refund` 在 `100` 之后吞吐没有继续提升，`ship-reminder` 在 `50` 之后已出现收益下降
3. 因此现阶段不建议仅因默认配置存在 `200` 就直接把三类任务统一抬到 `200`

---

## 6. 重试间隔建议

| 任务类型 | 当前配置 | 建议 | 原因 |
|---|---|---|---|
| ship-timeout | `120s` 固定延迟 | 保持 `120s` | 失败多为业务前置条件异常；自动重试不宜过密，人工 `trigger-now` 已能秒级恢复 |
| refund | `120s` 固定延迟 | 保持 `120s` | 失败多为记账输入异常；修数后 `reset` 可立即恢复，后台重试保持 120s 足够 |
| ship-reminder | `2m / 5m / 15m / 30m`，`running-timeout=5m` | 保持现状 | 本轮已验证 `FAILED` 手工补偿和 `RUNNING` 自动回收都可闭环，无需缩短间隔制造额外抖动 |

---

## 7. DoD 验收

DoD-1：三类任务均有运行态样本

- [x] 通过
- 证据：
  1. 三类任务均完成 `10 / 25 / 50 / 100` 多轮 `run-once`
  2. 已补跑 `150 / 200`，覆盖更高批量档位
  3. `ship-reminder` 额外补充 `RUNNING stale` 样本，覆盖回收分支

DoD-2：失败恢复路径可复现并可追踪

- [x] 通过
- 证据：
  1. `ship-timeout`：`PENDING + invalid_order_status -> trigger-now -> DONE`
  2. `refund`：`FAILED + invalid amount -> reset -> SUCCESS`
  3. `ship-reminder`：`FAILED + invalid_order_status -> trigger-now -> SUCCESS`
  4. `ship-reminder`：`RUNNING stale -> FAILED(running_timeout_recycle) -> 自动重试 -> SUCCESS`

DoD-3：执行后环境可回收

- [x] 通过
- 复核结果：
  1. `ship_timeout_due = 0`
  2. `refund_due = 0`
  3. `ship_reminder_due = 0`
  4. `ship_reminder_stale_running = 0`
  5. `DAY19-P4-S2-*` 测试订单残留 = `0`
- 说明：上述口径取自各轮压测执行结束时刻；后续若真实业务任务因 `next_retry_time/remind_time` 到达而重新进入 `due`，不视为本次合成样本残留

---

## 8. 结论

1. 三类任务在本地单节点环境下，`run-once` 到 `200` 批量均保持零失败
2. `ship-timeout / refund` 可先按 `100/min/job` 作为保守稳态预算
3. `ship-reminder` 建议先按 `50/min/job` 作为保守稳态预算
4. `trigger-now / reset` 可将约 `120s` 的默认等待窗口压缩到亚秒级恢复
5. `ship-reminder` 的 `RUNNING` 回收链路已可复现、可追踪、可自动恢复
6. `150 / 200` 虽然没有带来失败，但也没有优于原推荐批量，因此维持现有建议不变

（文件结束）
