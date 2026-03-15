# FrontDay06 接口对齐

- 日期：`2026-03-15`
- 文档版本：`v1.2`
- 当前状态：`已完成并回填`

---

## 1. 对齐结论

Day06 本轮只覆盖**只读接口**，而这部分已经完成并回填。`OpsCenter.vue` 的真实运行快照由多个 GET 结果聚合得到；`SystemSettings.vue` 本轮不接任何新增后端接口。

---

## 2. 已确认的真实读接口（实测日期：2026-03-14）

| 领域 | 接口 | 页面消费方式 | 实测结论 |
|---|---|---|---|
| Outbox 指标 | `GET /admin/ops/outbox/metrics` | 展示 `new / sent / fail / failRetrySum` | `code=1`，返回 `new=0 / sent=84 / fail=0 / failRetrySum=0` |
| 发货超时任务 | `GET /admin/ops/tasks/ship-timeout?page=1&pageSize=1` | 只取 `total` 形成概览卡片 | `code=1`，返回 `total=29` |
| 退款任务 | `GET /admin/ops/tasks/refund?page=1&pageSize=1` | 只取 `total` 形成概览卡片 | `code=1`，返回 `total=29` |
| 发货提醒任务 | `GET /admin/ops/tasks/ship-reminder?page=1&pageSize=1` | 只取 `total` 形成概览卡片 | `code=1`，返回 `total=54` |
| 订单快照 | `GET /admin/orders?page=1&pageSize=1` | 只取 `total` 作为订单总量 | `code=1`，返回 `total=68` |
| 违规统计 | `GET /admin/users/user-violations/statistics` | 读取 Top1 违规类型与数量 | `code=1`，Top1=`ship_timeout`，`count=1795` |
| SystemSettings | `无 Day06 新增接口` | 保持静态配置概览 | 代码中未接 `request` / `src/api` 调用 |

---

## 3. 页面侧兼容口径

1. Day06 **不执行** `publish-once / run-once`，只验证 GET 查询链路。
2. `OpsCenter.vue` 对只读快照改成“聚合 + availability 标记 + 局部降级”：
   - 任一 GET 失败，不让整页崩掉；
   - 失败卡片展示 `—` 或“待刷新”；
   - 页面通过 warning / danger banner 告知缺失源。
3. `GET /admin/users/user-violations/statistics` 当前可能返回 `violationTypeDesc=null`，页面应回退到 `violationType` 展示。
4. `SystemSettings.vue` 维持静态说明、跳转和弹窗逻辑，不在 Day06 伪造 settings controller。

---

## 4. 本轮仍未覆盖但不阻塞 Day06 的接口

| 接口 | 原因 | 当前定位 |
|---|---|---|
| `POST /admin/ops/outbox/publish-once` | 真实副作用，不属于本轮只读验证 | 后续联调 |
| `POST /admin/ops/tasks/ship-timeout/run-once` | 真实副作用，不属于本轮只读验证 | 后续联调 |
| `POST /admin/ops/tasks/refund/run-once` | 真实副作用，不属于本轮只读验证 | 后续联调 |
| `POST /admin/ops/tasks/ship-reminder/run-once` | 真实副作用，不属于本轮只读验证 | 后续联调 |

---

## 5. 结论

FrontDay06 本轮已经证明：`OpsCenter.vue` 的真实读接口是可用的，且页面具备单接口失败时的容错收口；`SystemSettings.vue` 则继续保持无后端接口的静态边界。因此 Day06 可以按既定范围认定为 `已完成并回填`。
