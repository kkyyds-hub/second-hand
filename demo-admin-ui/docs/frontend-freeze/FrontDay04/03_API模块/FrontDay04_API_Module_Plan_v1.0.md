# FrontDay04 API 模块计划

- 日期：`2026-03-13`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填`

---

## 1. 模块目标

把当天相关的页面能力落到明确的 API 文件、字段映射、错误处理和 mock/real 策略上。

---

## 2. 重点文件

| 文件 | 角色 | 当日要求 |
|---|---|---|
| `demo-admin-ui/src/pages/audit/AuditCenter.vue` | 纠纷与违规页主页面 | 包含筛选、详情、处理弹窗。 |
| `demo-admin-ui/src/api/audit.ts` | 纠纷与违规 API 模块 | 统一处理 DISPUTE/REPORT 两类动作。 |
| `demo-admin-ui/src/pages/ops/OpsCenter.vue` | 运维中心页面 | 包含运行态快照与手动动作入口。 |
| `demo-admin-ui/src/api/adminExtra.ts` | 运维与统计 API 模块 | 承接 outbox、tasks、orders、statistics 等接口。 |
| `demo-admin-ui/docs/backend-real-linkup.md` | 纠纷与违规真实接口说明 | 记录了总览、仲裁、举报处理接口。 |

---

## 3. 当日 API 规则

1. Audit 页面统一处理入口继续集中在 `src/api/audit.ts`，不要把不同工单类型逻辑散回页面。
2. 若 `REPORT` 类型缺少稳定 `ticketNo`，只能标记为待处理或待联调，不可伪造已完成。
3. OpsCenter 里的动作按钮需要明确“代码已接入”和“运行态已验证”是两层状态。

---

## 4. 预期输出

1. 接口口径与页面行为对应关系明确。
2. 字段映射、兜底、错误处理位置清晰。
3. 后续若改代码，可直接定位到当天关联文件。
