# UserFrontDay08 API 模块规划

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`计划中（v1.0 已建档，执行未开始）`

---

## 1. 模块目标

把 `共享交互、错误治理、系统通知与 API 模块整治` 落到清晰的 API 模块、页面消费边界、证据目录或 handoff 引用规则上。

---

## 2. 重点文件（规划态）

| 文件 | 当前状态 | 角色 | 说明 |
|---|---|---|---|
| `demo-user-ui/src/api/messages.ts` | 计划新增 / 扩展 | 承接未读数、系统通知列表、详情与全部已读 | 订单会话能力可在 Day06 先规划，Day08 继续扩展通知子域。 |
| `demo-user-ui/src/utils/request.ts` | Day01 已存在 | 继续承接全局请求错误与 401 规则 | Day08 只补共享治理口径。 |
| `demo-user-ui/src/pages/messages/SystemNoticePage.vue` | 计划新增 | 系统通知列表 / 详情主页面 | 当前线程只建规划。 |
| `demo-user-ui/src/components/*` | 计划新增 / 规范待定 | 承接空态、错误态、loading、submit 反馈的共享抽象 | 当前线程只冻结规则，不创建组件代码。 |
| `demo-user-ui/src/api/*.ts` | 部分计划新增 | 承接 Day02~Day07 模块命名与消费边界整治 | 只定规则，不提前声称模块已全部实现。 |

---

## 3. API / 资料规则

1. 先分域或分层规划，不把不同角色、不同阶段的链路混写。
2. 字段适配、错误映射、证据目录或 handoff 引用优先在本日文档里收口。
3. 未经真实执行，不把规划文件写成已完成实现或已完成 handoff。

---

## 4. 当前字段与适配口径

- 通知 badge 刷新、已读联动与空态规则待执行时确认。
- 共享治理如果影响 Day01 request 基线，必须先回填 Day01 再同步 Day08。
- orders / afterSales / messages / wallet / points / credit 的最终模块命名待执行时确认。
