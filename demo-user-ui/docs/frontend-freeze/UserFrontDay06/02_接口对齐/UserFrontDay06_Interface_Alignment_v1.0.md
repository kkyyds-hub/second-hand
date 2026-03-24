# UserFrontDay06 前后端接口对齐

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`计划中（v1.0 已建档，执行未开始）`

---

## 1. 对齐目标

围绕 `卖家订单、发货、物流、售后处理与订单会话` 冻结本日需要承接的接口入口，并明确当前还没有任何对应前端实现落地。

---

## 2. 接口清单

| 场景 | 接口 / 契约 | 计划口径 | 备注 |
|---|---|---|---|
| 卖家订单列表 | `GET /user/orders/sell` | 作为卖家订单列表页主入口。 | 查询字段待执行时确认。 |
| 订单详情 | `GET /user/orders/{orderId}` | 作为卖家订单详情主入口。 | 与 Day05 共享详情接口，但角色语义不同。 |
| 物流查看 | `GET /user/orders/{orderId}/logistics` | 作为卖家物流查看主入口。 | 买家若需查看，只回引该语义。 |
| 卖家发货 | `POST /user/orders/{orderId}/ship` | 作为卖家发货动作入口。 | 执行时补表单字段与失败态。 |
| 卖家售后处理 | `PUT /user/after-sales/{afterSaleId}/seller-decision` | 作为 seller after-sales 入口。 | 买家售后发起不在 Day06。 |
| 订单会话消息 | `POST /user/messages/orders/{orderId}`、`GET /user/messages/orders/{orderId}`、`PUT /user/messages/orders/{orderId}/read` | 作为 order chat 入口。 | 系统通知与未读总入口留给 Day08。 |

---

## 3. 对齐原则

1. API 模块先分域规划，不把不同角色链路混写。
2. 继续复用 Day01 的鉴权、401 与 `authentication` 请求头规则。
3. 所有失败态、空态、阻塞说明都要保留在文档与后续回填里。
4. 字段级不确定项一律写成“待执行时确认”。

---

## 4. 已知缺口 / 边界说明

- 当前还没有对应页面、路由与 API 模块实现。
- 当前只有控制器与计划文档，尚无 build / runtime / 联调证据。
- 若执行中发现接口语义变化，必须先更新本文档再进入实现线程。
