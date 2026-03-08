# Day19 P4-S1 Outbox 发布性能报告 v1.0

- 日期：`2026-03-08`
- 对应范围：`Day19_Scope_Freeze_v1.0.md -> Phase 4 / Step P4-S1`
- 当前状态：`已执行完成（2026-03-08 04:34:39）`
- 动态证据：`day19回归/执行记录/Day19_P4_S1_动态结果_2026-03-08_04-34-39.json`
- 执行脚本：`day19回归/执行复现步骤/Day19_P4_S1_Outbox发布性能_执行复现_v1.0.ps1`

---

## 1. 目标与口径

本步骤目标：把 Outbox 从“可用”提升为“可量化、可治理”，覆盖三类证据：

1. `publish-once` 在不同 `limit` 下的单轮吞吐与失败率
2. `new / sent / fail / failRetrySum` 的变化曲线
3. “发现积压 -> 定位事件 -> trigger-now -> publish-once -> 指标恢复”的排障闭环

统一口径：

1. 发布成功口径：`publish-once` 返回 `sent = limit` 且 `failed = 0`
2. 吞吐口径：`sent / elapsedSec`
3. 失败率口径：`failed / pulled`
4. 监控口径：复用 `/admin/ops/outbox/metrics`

---

## 2. 执行环境与前置

执行环境：

1. 服务地址：`http://localhost:8080`
2. 管理员账号：`13900000001 / admin123`
3. RabbitMQ 端口：`5672` 可达
4. Outbox 基线：`new=0, sent=81, fail=0, failRetrySum=0`

安全样本策略：

1. 成功样本使用 `order.events.exchange`
2. routingKey 使用无业务绑定的安全值，例如 `day19.outbox.safe.*`
3. 这样可以真实覆盖 `RabbitTemplate.convertAndSend(...)`，同时避免压测消息进入现有业务消费者

---

## 3. 执行参数冻结

- 成功样本 `limit`：`10 / 25 / 50 / 100`
- 每个 `limit` 轮次：`3`
- 失败曲线样本：`12`
- 恢复闭环样本：`8`
- 失败阈值参考：`fail-threshold=5`
- 重试阈值参考：`fail-retry-threshold=10`

---

## 4. publish-once 基线结果

### 4.1 汇总结果

| limit | 平均吞吐(msg/s) | P50 吞吐(msg/s) | 平均耗时(ms) | 总失败数 |
|---|---:|---:|---:|---:|
| 10 | 233.26 | 232.23 | 43.77 | 0 |
| 25 | 430.54 | 491.06 | 61.03 | 0 |
| 50 | 628.24 | 635.81 | 79.63 | 0 |
| 100 | 899.04 | 953.83 | 115.39 | 0 |

### 4.2 分轮明细

| limit | 轮次 | 耗时(ms) | pulled | sent | failed | 吞吐(msg/s) |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1 | 51.82 | 10 | 10 | 0 | 192.98 |
| 10 | 2 | 43.06 | 10 | 10 | 0 | 232.23 |
| 10 | 3 | 36.42 | 10 | 10 | 0 | 274.57 |
| 25 | 1 | 50.91 | 25 | 25 | 0 | 491.06 |
| 25 | 2 | 50.60 | 25 | 25 | 0 | 494.07 |
| 25 | 3 | 81.57 | 25 | 25 | 0 | 306.49 |
| 50 | 1 | 82.21 | 50 | 50 | 0 | 608.20 |
| 50 | 2 | 78.64 | 50 | 50 | 0 | 635.81 |
| 50 | 3 | 78.04 | 50 | 50 | 0 | 640.70 |
| 100 | 1 | 104.84 | 100 | 100 | 0 | 953.83 |
| 100 | 2 | 147.42 | 100 | 100 | 0 | 678.33 |
| 100 | 3 | 93.90 | 100 | 100 | 0 | 1064.96 |

### 4.3 结果解释

1. `limit` 增大后，吞吐近似单调上升
2. `limit=100` 在本地样本规模下表现最好，且 `3` 轮全部 `0` 失败
3. `100 -> 50` 不是“收益很小”的边际提升，而是平均吞吐从 `628.24` 提升到 `899.04`，增幅约 `43.11%`

结论：当前环境下推荐把 `outbox.publish.batch-size` 从 `50` 提升到 `100`。

---

## 5. 指标变化曲线

### 5.1 失败注入场景

失败样本说明：

1. 插入 `12` 条 `NEW` 状态坏 JSON 样本
2. 第一次 `publish-once`：全部进入 `FAIL`
3. 第二次强制到期后再次 `publish-once`：继续失败，`retry_count` 继续累加

### 5.2 曲线快照

| 阶段 | new | sent | fail | failRetrySum | 批次 NEW | 批次 SENT | 批次 FAIL | 批次重试和 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| baseline | 0 | 81 | 0 | 0 | 0 | 0 | 0 | 0 |
| after_seed | 12 | 81 | 0 | 0 | 12 | 0 | 0 | 0 |
| after_publish_1 | 0 | 81 | 12 | 12 | 0 | 0 | 12 | 12 |
| after_publish_2 | 0 | 81 | 12 | 24 | 0 | 0 | 12 | 24 |
| after_cleanup | 0 | 81 | 0 | 0 | 0 | 0 | 0 | 0 |

失败发布结果：

1. 第一次 `publish-once`：`pulled=12, sent=0, failed=12, elapsed=81.10ms`
2. 第二次 `publish-once`：`pulled=12, sent=0, failed=12, elapsed=70.34ms`

结论：

1. `new -> fail` 与 `failRetrySum` 累加曲线清晰可观测
2. 当前阈值 `fail>=5 or failRetrySum>=10` 在本次注入场景下会触发告警
3. 该阈值能有效区分“正常空闲”与“异常积压”

---

## 6. 积压异常排障闭环

### 6.1 场景设计

1. 插入 `8` 条 `FAIL` 状态、`next_retry_time` 在未来的合法 Outbox 样本
2. 先通过 `metrics` 观察到失败积压
3. 再按 `eventId` 调用 `/event/{eventId}` 查询样本
4. 对该批事件逐条调用 `/trigger-now`
5. 最后执行一次 `publish-once`

### 6.2 闭环结果

| 指标 | 值 |
|---|---:|
| Seed 后 FAIL | 8 |
| Seed 后 failRetrySum | 16 |
| trigger-now 调用数 | 8 |
| 恢复发布耗时(ms) | 40.51 |
| 恢复发布 sent | 8 |
| 发布后 FAIL | 0 |
| 发布后 failRetrySum | 0 |

样本事件状态：

1. `sampleEventId = DAY19-P4-S1-RECOVERY-001`
2. trigger 前状态：`FAIL`
3. 发布后状态：`SENT`

结论：Outbox 积压异常的最小闭环已经跑通，满足“可执行排障”要求。

---

## 7. 参数建议

### 7.1 batch-size

建议值：`outbox.publish.batch-size = 100`

原因：

1. `limit=100` 平均吞吐最高：`899.04 msg/s`
2. 全部轮次 `0` 失败
3. 对比当前默认值 `50`，吞吐提升约 `43.11%`

### 7.2 告警阈值

建议分层：

1. **观察阈值**
   - `fail >= 3`
   - `failRetrySum >= 6`

2. **告警阈值**
   - `fail >= 5`
   - `failRetrySum >= 10`

建议：

1. 保留当前 `5 / 10` 作为正式告警阈值
2. 在文档或运维脚本中增加 `3 / 6` 作为人工观察阈值

理由：

1. 基线场景长期为 `0 / 0`
2. 注入 `12` 条失败时，当前阈值能稳定触发
3. 对个人项目本地规模来说，`5 / 10` 不算过松

---

## 8. 积压异常排障 SOP

1. 调用 `/admin/ops/outbox/metrics`
   - 看 `new / fail / failRetrySum`
2. 若 `fail >= 5` 或 `failRetrySum >= 10`
   - 进入事件级排查
3. 调用 `/admin/ops/outbox/event/{eventId}`
   - 看 `status / retryCount / nextRetryTime / exchange / routingKey`
4. 若确认消息本身可重试
   - 调用 `/admin/ops/outbox/event/{eventId}/trigger-now`
5. 再执行 `/admin/ops/outbox/publish-once?limit=100`
6. 最后再看 `/metrics`
   - 预期 `fail` 下降、`sent` 上升

---

## 9. DoD 验收

| DoD | 验收结果 | 证据 |
|---|---|---|
| 能给出可执行的 batch-size 参数建议 | 通过 | 第 4、7 章，建议 `100` |
| 能给出“积压异常”排障闭环 | 通过 | 第 6、8 章，`FAIL -> trigger-now -> SENT` 已实测 |

---

## 10. 产物清单

1. `day19回归/步骤规划/Day19_P4_S1_Outbox发布性能报告_v1.0.md`
2. `day19回归/执行记录/Day19_P4_S1_动态结果_2026-03-08_04-34-39.json`
3. `day19回归/执行复现步骤/Day19_P4_S1_Outbox发布性能_执行复现_v1.0.ps1`

---

（文件结束）
