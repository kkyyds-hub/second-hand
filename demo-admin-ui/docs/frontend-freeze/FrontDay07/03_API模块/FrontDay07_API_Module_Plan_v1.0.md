# FrontDay07 API 模块计划

- 日期：`2026-03-15`
- 文档版本：`v1.1`
- 当前状态：`已完成并回填`

---

## 1. 模块目标

把当天相关的页面能力落到明确的 API 文件、字段映射、错误处理和 mock/real 策略上。

---

## 2. 重点文件

| 文件 | 角色 | 当日要求 |
|---|---|---|
| `demo-admin-ui/src/style.css` | 全局样式与通用 utility | 需要评估哪些规则沉淀到全局。 |
| `demo-admin-ui/src/pages/users/UserList.vue` | 列表与弹窗典型页面 | 可作为规范抽样页面。 |
| `demo-admin-ui/src/pages/products/ProductReview.vue` | 审核弹窗与列表页面 | 用于验证表格和弹窗规范。 |
| `demo-admin-ui/src/pages/audit/AuditCenter.vue` | 详情/处理弹窗页面 | 用于验证复杂交互规范。 |

---

## 3. 当日 API 规则

1. 尽量复用已有 utility class，不平地起新的命名体系。
2. 交互规范先服务现有页面，不为抽象而抽象。
3. 规范落地后要把影响页面写清楚，避免只有口号没有适用面。

---

## 4. 实际输出

1. 当天未新增 API 文件改造，页面规范统一主要落在 `src/style.css` 与各页面组件上。
2. 受影响页面、弹窗、表格、空态与 loading 行为已经能直接回溯到当天关联文件。
3. 后续若进入 FrontDay08 做 API 模块治理，可直接基于本日收口后的页面继续推进。
