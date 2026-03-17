# 管理端真实后端联调说明

## 启动方式

### 1) 纯前端 Mock 模式

```bash
npm run dev:mock
```

说明：
- 使用本地 mock 数据；
- 适合后端还没启动时继续做页面开发；
- 商品审核、用户管理、纠纷列表都可在前端本地跑流程。

### 2) 真实后端联调模式

```bash
npm run dev:real
```

说明：
- `VITE_USE_MOCK=false`
- 前端通过 Vite 代理把 `/api/*` 转发到 `http://localhost:8080`
- 你的 Spring Boot 后端默认端口也是 `8080`

---

## 当前已对齐的真实接口

### 登录
- `POST /admin/employee/login`

请求体：

```json
{
  "loginId": "手机号或邮箱",
  "password": "密码"
}
```

---

### 首页总览
- `GET /admin/dashboard/overview`
- `GET /admin/statistics/dau`
- `GET /admin/statistics/order-gmv`
- `GET /admin/statistics/product-publish`

说明：
- Dashboard 当前由 `overview + statistics` 聚合成首页只读快照。
- `2026-03-14` 实测中，`overview` 的 `disputeQueue` 返回真实 `0`；`dau / order-gmv / product-publish` 也都返回真实 `0` 值时，前端按真实值展示，不再强造 demo 数字。
- 如果 statistics 子接口有缺失，前端通过 `fetchHomeStatisticsBundle()` 保留成功返回的数据，并用 availability 标记提示缺口。
- 当 `overview.disputeQueue` 为空但 `GET /admin/audit/overview?riskLevel=HIGH` 仍有高优工单时，前端会补做一次只读聚合，把前 3 条高优审计工单映射到首页“高优处理队列”，避免首页误显示为空白。
- `2026-03-15` 在当前管理员重新登录后的真实页面走查中，前端实际触发了 `/api/admin/dashboard/overview?date=2026-03-15`、`/api/admin/statistics/dau?date=2026-03-15`、`/api/admin/statistics/order-gmv?date=2026-03-15`、`/api/admin/statistics/product-publish?date=2026-03-15` 与 `/api/admin/audit/overview?riskLevel=HIGH`，均返回 HTTP `200`。
- 同轮页面实测中，Dashboard 展示 `GMV=¥0 / 订单数=0 / 发布量=0` 的真实结果，并保留“高优处理队列”展示高优工单兜底。

---

### 运维中心（FrontDay06 只读联调）
- `GET /admin/ops/outbox/metrics`
- `GET /admin/ops/tasks/ship-timeout?page=1&pageSize=1`
- `GET /admin/ops/tasks/refund?page=1&pageSize=1`
- `GET /admin/ops/tasks/ship-reminder?page=1&pageSize=1`
- `GET /admin/orders?page=1&pageSize=1`
- `GET /admin/users/user-violations/statistics`

说明：
- `2026-03-14` 实测上述 6 个 GET 都返回 `code=1`。
- 当前实测结果：
  - Outbox 指标：`new=0 / sent=84 / fail=0 / failRetrySum=0`
  - 发货超时任务：`total=29`
  - 退款任务：`total=29`
  - 发货提醒任务：`total=54`
  - 订单快照：`total=68`
  - 违规统计 Top1：`ship_timeout`，`count=1795`
- `GET /admin/users/user-violations/statistics` 当前可能返回 `violationTypeDesc=null`，前端会回退到 `violationType` 展示。
- `OpsCenter.vue` 通过 `fetchOpsRuntimeBundle()` 聚合多个 GET；任一接口失败时，只降级对应卡片，不让整页崩掉。
- `2026-03-16` 已补前端摘要口径修正：运行概览改按状态过滤汇总 `ship-timeout(PENDING)`、`refund(PENDING+FAILED)`、`ship-reminder(PENDING+FAILED)`，避免把历史 `SUCCESS / DONE / CANCELLED` 记录误判成待处理。
- `2026-03-16` 在当前管理员真实页面联调中，前端加载 OpsCenter 后页面实际展示 `订单总量=70 / Outbox 待发送=0 / Outbox 失败=7 / 发货超时未完成=3 / 退款待重试=0 / 发货提醒待补跑=0 / Top1=ship_timeout(1795)`。
- `2026-03-16` 已执行真实写动作：
  - `POST /admin/ops/outbox/publish-once?limit=50`：返回 `pulled=0 / sent=0 / failed=0`
  - `POST /admin/ops/tasks/refund/run-once?limit=50`：返回 `success=0 / batchSize=50`
- 对应页面证据已落到 FrontDay09：`FrontDay09_opscenter_write_actions_2026-03-16.json`、`FrontDay09_opscenter_write_actions_live_2026-03-16.png`。

---

### 系统设置

说明：
- `SystemSettings.vue` 当前仍是静态配置概览页。
- FrontDay06 没有新增 `/admin/settings/*` 之类的真实后端接口，也没有伪造协议来充数。
- `2026-03-15` 认证态页面走查中确认该页未发起 `/admin/settings/*` 请求，仍保持静态配置概览边界。

---

### 用户管理
- `GET /admin/user`
- `POST /admin/user`
- `PUT /admin/user/{userId}/ban?reason=...`
- `PUT /admin/user/{userId}/unban`
- `GET /admin/user/export`

---

### 商品审核
- `GET /admin/products/pending-approval`
- `PUT /admin/products/{productId}/approve`
- `PUT /admin/products/{productId}/reject`

说明：
- 驳回接口需要请求体：

```json
{
  "reason": "驳回原因"
}
```

- 前端页面已补驳回原因弹窗，可直接联调。

---

### 纠纷与违规
- `GET /admin/audit/overview`
- `PUT /admin/after-sales/{afterSaleId}/arbitrate`
- `PUT /admin/products/reports/{ticketNo}/resolve`

说明：
- 当前真实后端已提供总览查询与两类写动作接口；
- 页面列表/筛选/详情可直接联调；
- 页面已补真实处理弹窗：
  - 交易纠纷：支持售后 / 驳回售后
  - 违规举报：举报不成立 / 强制下架商品
- 风控线索当前仍以“查询联调”为主，未接统一写入动作。
- `2026-03-15` 在当前管理员重新登录后的真实页面走查中，`GET /admin/audit/overview` 返回 HTTP `200 / code=1`，当前 `stats=pendingDisputes 0 / urgentReports 2 / platformIntervention 0 / todayNewClues 0`，`tickets=120`；页面可正常渲染筛选区与工单列表。
- `2026-03-16` 已补齐两条真实写动作证据：
  - `PUT /admin/products/reports/RPT-20260316-235856/resolve`：页面执行“举报不成立”后，工单状态从 `PENDING` 变为 `CLOSED / RESOLVED_INVALID`；
  - `PUT /admin/after-sales/3/arbitrate`：页面执行“支持售后申请”后，售后纠纷状态从 `DISPUTED` 变为 `CLOSED`。
- 对应页面证据已落到 FrontDay09：`FrontDay09_auditcenter_report_dismiss_2026-03-16.json`、`FrontDay09_auditcenter_dispute_arbitrate_2026-03-16.json`。
---

## 当前真实联调时的已知限制

### 1. 商品审核列表字段不完全齐
后端 `ProductDTO` 当前稳定返回：
- `productId`
- `productName`
- `category`
- `status`
- `submitTime`

前端已做兼容：
- 卖家名缺失时显示 `--`
- 价格缺失时显示 `--`
- 风险等级按类目做前端兜底展示

### 2. 举报工单依赖 ticketNo
前端当前用总览返回的 `id` 作为举报处理时的 `ticketNo`。

如果后端某条举报工单没有生成 `ticketNo`，而是退回了类似 `RPT-数字ID` 的兜底值，
则该条工单的“立即处理”接口可能无法命中真实工单编号。

### 3. OpsCenter 写动作已完成最小真实验证
`2026-03-16` 已完成 `publish-once + refund run-once` 的真实页面联调；
当前若还要继续补强 OpsCenter，只剩“RabbitMQ 启动后验证真实消息投递”这类环境增强项，不再属于 Day09 主页面动作链路阻塞。

---

## 推荐验证顺序

1. 先起后端（确认是 `localhost:8080`）
2. 前端运行：

```bash
npm run dev:real
```

3. 验证顺序：
   1. 登录
   2. 首页总览
   3. 运维中心只读快照
   4. 用户列表 / 筛选 / 封禁 / 解封
   5. 商品审核列表 / 通过 / 驳回
   6. 纠纷与违规页列表 / 筛选 / 详情

---

## 如果联调失败优先排查

1. 后端是否运行在 `http://localhost:8080`
2. 登录是否返回 `token`
3. 请求头是否带了 `token`
4. 后端统一返回是否仍是：

```json
{
  "code": 1,
  "msg": "success",
  "data": {}
}
```

5. 商品审核列表是否返回 `PageResult`


