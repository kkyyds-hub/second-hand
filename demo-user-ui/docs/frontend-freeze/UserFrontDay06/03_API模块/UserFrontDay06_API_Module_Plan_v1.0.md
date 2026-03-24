# UserFrontDay06 API 模块规划

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`计划中（v1.0 已建档，执行未开始）`

---

## 1. 模块目标

把 `卖家订单、发货、物流、售后处理与订单会话` 落到清晰的 API 模块、页面消费边界和错误处理规则上。

---

## 2. 重点文件（规划态）

| 文件 | 当前状态 | 角色 | 说明 |
|---|---|---|---|
| `demo-user-ui/src/api/orders.ts` | 计划新增 | 继续承接卖家订单详情、物流与发货 | 在 Day05 订单规划基础上补 seller 侧消费边界。 |
| `demo-user-ui/src/api/afterSales.ts` | 计划新增 | 承接 seller decision 与售后处理 | 与 Day05 共用模块，但角色与入口不同。 |
| `demo-user-ui/src/api/messages.ts` | 计划新增 | 承接订单会话消息与已读逻辑 | 系统通知可在 Day08 继续扩展。 |
| `demo-user-ui/src/pages/orders/SellerOrdersPage.vue` | 计划新增 | 卖家订单列表主页面 | 当前线程只建规划。 |
| `demo-user-ui/src/pages/orders/SellerOrderDetailPage.vue` | 计划新增 | 发货、物流、售后处理与聊天入口承载页 | 需要统一 seller 侧动作分区。 |

---

## 3. API 规则

1. 按业务域拆分 API 模块，不把跨日角色混写。
2. 字段适配与错误映射优先留在 API 层，不分散到页面。
3. 未经真实执行，不把规划文件写成已完成实现。

---

## 4. 当前字段与适配口径

- 卖家发货表单字段、物流轨迹展示与 seller decision 提示语待执行时确认。
- 订单会话只处理 order chat，不替代系统通知中心。
- 若订单消息和通知边界变化，必须同步回填 Day06 与 Day08。
