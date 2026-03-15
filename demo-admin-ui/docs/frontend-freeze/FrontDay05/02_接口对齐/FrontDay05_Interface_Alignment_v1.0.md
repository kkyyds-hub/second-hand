# FrontDay05 接口对齐

- 日期：`2026-03-14`
- 文档版本：`v1.1`
- 当前状态：`已完成并回填`

---

## 1. 对齐结论

Day05 的接口对齐目标已经完成：首页真实接口、统计接口、本地趋势图和兜底队列的边界已明确写清，不再把“部分真实、部分占位”的现状混写成“全部真实化完成”。

---

## 2. 已确认的接口与口径

| 场景 | 接口 / 契约 | 当前口径 | 备注 |
|---|---|---|---|
| 首页总览 | `GET /admin/dashboard/overview` | 提供 `coreMetrics / reviewQueue / disputeQueue / riskAlerts` 的聚合只读数据。 | 首页主聚合接口。 |
| 首页统计 | `GET /admin/statistics/dau` | 由 `fetchHomeStatisticsBundle()` 聚合读取。 | 失败时用 availability 标记缺口。 |
| 首页统计 | `GET /admin/statistics/order-gmv` | 读取 `orderCount / gmv`。 | `0` 值按真实结果展示。 |
| 首页统计 | `GET /admin/statistics/product-publish` | 当前只冻结 `total` 在首页的展示口径。 | `byCategory` 不属于 Day05 退出标准。 |
| 高优处理队列兜底 | `GET /admin/audit/overview?riskLevel=HIGH` | 当 `overview.disputeQueue` 为空但仍有高优工单时，可补做只读聚合兜底。 | 避免首页误显示为空白。 |
| 趋势图 | 本地 `mockTrendData` | 继续保留为本地趋势占位。 | 不写成“已接真实趋势接口”。 |

---

## 3. 当前稳定边界

1. `overview + statistics` 已构成首页核心真实只读快照。
2. `0` 值属于真实结果时，前端按真实值展示，不再强造 demo 数字。
3. 趋势图与更细的扩展统计仍属于后续补强项，不作为 Day05 未完成的理由。
4. Dashboard 业务域整体仍可继续演进，但 Day05 这一天的“边界冻结”目标已经达成。

---

## 4. 后续跟进但不阻塞 Day05 的项

1. 趋势图真实数据替换。
2. `product-publish.byCategory` 等更细粒度扩展统计的消费策略。
3. 更大范围的 Dashboard 视觉回归与联调回归。

---

## 5. 结论

Day05 已完成并回填。后续若继续补 Dashboard 的更大范围运行证据，应作为后续执行日任务推进，而不是继续占用 Day05 的状态定义。
