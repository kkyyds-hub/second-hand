# FrontDay04 前后端接口对齐

- 日期：`2026-03-13`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填`

---

## 1. 对齐目标

围绕 `纠纷与违规、运维入口冻结`，固定当天前端需要依赖的接口、字段与已知缺口。

---

## 2. 接口清单

| 场景 | 接口 / 契约 | 当前口径 | 备注 |
|---|---|---|---|
| 纠纷与违规总览 | `GET /admin/audit/overview` | 返回统计卡和工单列表。 | 当前为页面主聚合接口。 |
| 售后裁决 | `PUT /admin/after-sales/{afterSaleId}/arbitrate` | 用于 DISPUTE 类型工单处理。 | 依赖 `sourceId`。 |
| 举报处理 | `PUT /admin/products/reports/{ticketNo}/resolve` | 用于 REPORT 类型工单处理。 | 依赖稳定 `ticketNo`。 |
| Outbox 指标 | `GET /admin/ops/outbox/metrics` | 运维中心展示当前积压/失败/已发送量。 | 已在 `adminExtra.ts` 接入。 |
| Outbox 手动执行 | `POST /admin/ops/outbox/publish-once` | 运维中心可触发一次补发。 | 运行态仍需确认。 |
| 任务执行 | `POST /admin/ops/tasks/*/run-once` | 支持发货超时、退款、发货提醒等 run-once。 | 代码已接入，待统一验收。 |

---

## 3. 当日接口对齐原则

1. 优先引用真实接口文档与代码中的固定实现。
2. 已知后端字段缺口必须写入备注，不做乐观省略。
3. 接口变化后，应同步更新对应 API 模块文档和进度回填结论。

---

## 4. 当前已知风险 / 缺口

1. **举报工单 `ticketNo` 稳定性验证**：当前文档已记录 `ticketNo` 可能影响举报处理命中。
2. **运维动作统一运行态证据**：建议在 FrontDay06 或 FrontDay09 统一补充。
3. **风险线索写操作**：当前仅覆盖查询/展示，不作为 Day04 完成项。
