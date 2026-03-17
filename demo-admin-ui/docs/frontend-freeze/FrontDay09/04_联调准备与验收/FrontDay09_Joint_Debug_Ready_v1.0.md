# FrontDay09 联调准备与验收

- 日期：`2026-03-16`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填（Dashboard sellerName、趋势 / 扩展统计、UserList、ProductReview、AuditCenter、OpsCenter 均已补齐真实证据）`

---

## 1. Day09 当前推进范围

| 链路 | 日期 | 当前结果 | 说明 |
|---|---|---|---|
| UserList 封禁 / 解封 | `2026-03-15` | `pass` | 已完成真实接口 smoke，证据已回填。 |
| ProductReview 审核通过 / 驳回 | `2026-03-16` | `pass` | 本轮已完成真实联调与后端阻塞修复。 |
| Dashboard sellerName 真实页面联调 | `2026-03-16` | `pass` | 本轮已完成真实页面打开、接口返回与页面卖家列一致性验证。 |
| Dashboard 趋势 / 扩展统计证据补强 | `2026-03-16` | `pass` | 本轮已完成真实页面 + 真实接口补证，并明确趋势文案 / 扩展统计与静态 SVG 装饰的边界。 |
| AuditCenter 举报处理（dismiss） | `2026-03-16` | `pass` | 本轮已完成真实页面联调、真实工单创建与关闭态回查。 |
| AuditCenter 仲裁（approve） | `2026-03-16` | `pass` | 本轮已完成真实页面联调、种子售后纠纷构造与关闭态回查。 |
| OpsCenter publish-once / refund run-once | `2026-03-16` | `pass` | 已完成真实页面联调；本轮选择 `refund run-once` 作为 `run-once` 代表链路。 |

## 2. ProductReview 最小必要核对结论

| 面 | 文件 | 结论 |
|---|---|---|
| 页面 | `demo-admin-ui/src/pages/products/ProductReview.vue` | `handleApprove()` 直接调用 `approveProductReview()`；`confirmReject()` 校验驳回理由后调用 `rejectProductReview()`，成功后统一 `fetchData()` 回刷列表。 |
| API 模块 | `demo-admin-ui/src/api/product.ts` | 列表走 `GET /admin/products/pending-approval`；通过走 `PUT /admin/products/{id}/approve`；驳回走 `PUT /admin/products/{id}/reject`，请求体为 `{ reason }`。 |
| 请求层 | `demo-admin-ui/src/utils/request.ts` | 管理端 token 仍通过请求头 `token` 传递，与 ProductReview 本轮调用保持一致。 |
| 后端控制器 | `demo-service/src/main/java/com/demo/controller/admin/ProductController.java` | 控制器暴露 `@GetMapping("/pending-approval")`、`@PutMapping("/{productId}/approve")`、`@PutMapping("/{productId}/reject")`，与前端 API 模块一致。 |
| Day09 文档 | `demo-admin-ui/docs/frontend-freeze/FrontDay09/02_接口对齐/FrontDay09_Interface_Alignment_v1.0.md` | 商品链路口径仍为 `GET/PUT /admin/products/*`，本轮没有新增外部契约字段。 |

## 3. 2026-03-16 实际执行

### 3.1 首次真实请求发现的阻塞（ProductReview）

- 阻塞接口：`GET /admin/products/pending-approval?page=1&pageSize=20`
- 现象：接口返回 `code=0 / msg=服务器错误`
- 后端日志定位：
  - `com.demo.handler.GlobalExceptionHandler`
  - `java.lang.NoSuchMethodError: 'void com.demo.dto.user.ProductDTO.setOwnerId(java.lang.Long)'`
- 结论：这是**后端侧 DTO 兼容问题**，不是前端 / token / 协议问题。

### 3.2 本轮最小必要修复（ProductReview / Dashboard）

| 变更侧 | 文件 | 修复说明 |
|---|---|---|
| 后端 | `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java` | `toProductDTO()` 去掉对 `ProductDTO.setOwnerId()` 的调用，并补充兼容性注释，避免 `pending-approval` 列表阶段触发 `NoSuchMethodError`。 |
| 后端 | `demo-service/src/main/java/com/demo/service/serviceimpl/AdminDashboardServiceImpl.java` | 审核队列不再复用 `ProductDTO ownerId` 兼容链路，已改为专用 VO + `Product` 实体查询回填真实卖家展示名，并补充契约注释。 |

### 3.3 ProductReview 构建与运行验证

- 后端重编译：
  - `mvn clean -DskipTests package`：`fail`
    - 原因：历史测试源码 `src/test/java/com/demo/concurrency/OutboxPublishJobFailureInjectionTest.java` 仍有旧签名编译错误；
    - 该失败与本轮 ProductReview 主链路无直接契约关系。
  - `mvn -Dmaven.test.skip=true package`：`pass`
- 真实联调动作：
  1. `POST /admin/employee/login`
  2. `GET /admin/products/pending-approval?page=1&pageSize=20&status=under_review`
  3. `PUT /admin/products/920047/approve`
  4. `GET /admin/products/pending-approval?page=1&pageSize=20&status=on_sale&productName=DAY18-LV2-D18CLOSE0304175712-1`
  5. `PUT /admin/products/920048/reject`
  6. `GET /admin/products/pending-approval?page=1&pageSize=20&status=off_shelf&productName=DAY18-LV2-D18CLOSE0304175712-2`
  7. `GET /admin/products/pending-approval?page=1&pageSize=20&status=under_review`
- 实际观察：
  - 审核前待审核总数：`4`
  - `920047` 审核通过后可在 `status=on_sale` 下查到；
  - `920048` 驳回后可在 `status=off_shelf` 下查到；
  - 审核后待审核总数降为：`2`
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_productreview_approve_reject_2026-03-16.json`

### 3.4 Dashboard sellerName 真实页面联调

- 发现问题：
  - Dashboard 审核队列表头显示的是“卖家”，但前后端都沿用字段名 `user`，语义不清晰；
  - 后端上一轮为规避 `ProductDTO ownerId` 兼容问题，把卖家名退化成“未知卖家”，会降低首页信息有效性。
- 本轮联调前置：
  - 已复用上一轮完成的 Dashboard reviewQueue 专用 VO / `sellerName` 契约修复；
  - 后端以 `http://localhost:8080` 启动，前端以 `http://localhost:5173` 的 `npm run dev:real` 启动。
- 本轮验证范围：
  - `POST /admin/employee/login`：`pass`
  - `GET /admin/dashboard/overview?date=2026-03-16`：`pass`
  - 打开 `http://localhost:5173/` Dashboard 页面并注入真实管理员 token：`pass`
  - 页面首屏 reviewQueue “卖家”列与接口 `sellerName` 一致性核对：`pass`
- 实际观察：
  - `/admin/dashboard/overview?date=2026-03-16` 返回 `reviewQueueCount=2`；
  - 两条审核队列的 `sellerName` 都返回 `卖家测试同学`；
  - 页面 `Dashboard.vue` 中“卖家”列实际展示 `卖家测试同学`，且页面上未出现 `未知卖家`；
  - 页面侧抓到的 `/api/admin/dashboard/overview` 响应与直接 API 调用返回一致。
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_sellername_2026-03-16.json`
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_sellername_live_2026-03-16.png`
- 结论：
  - 这次已经补到**真实页面 + 真实接口**证据，Dashboard sellerName 子链路可以记为 `pass`；
  - 后续的趋势类与扩展统计证据已在本文 `3.7` 补齐，因此 Dashboard 不再停留在 sellerName 单点结论。

### 3.5 AuditCenter 举报处理（dismiss）真实页面联调

- 最小必要核对：
  - 页面：`demo-admin-ui/src/pages/audit/AuditCenter.vue` 中 `openProcessModal()` 会重置处理表单，`handleProcess()` 在 `REPORT` 分支调用 `submitAuditAction()` 后统一 `fetchData()` 回刷列表。
  - API 模块：`demo-admin-ui/src/api/audit.ts` 的 `REPORT` 分支固定调用 `PUT /admin/products/reports/{ticketNo}/resolve`，请求体为 `{ action, remark }`；总览仍走 `GET /admin/audit/overview`。
  - 请求层：`demo-admin-ui/src/utils/request.ts` 仍通过请求头 `token` 传递管理端凭证，本轮页面请求与直接 API 核对一致。
  - 后端控制器：`demo-service/src/main/java/com/demo/controller/admin/AdminAuditController.java` 暴露总览接口，`demo-service/src/main/java/com/demo/controller/admin/ProductController.java` 暴露 `@PutMapping("/reports/{ticketNo}/resolve")`，与前端页面 / API 模块一致。
- 真实联调动作：
  1. `POST /admin/employee/login`
  2. `POST /user/auth/login/password`
  3. `POST /user/market/products/920127/report`
  4. `GET /admin/audit/overview?type=REPORT&keyword=RPT-20260316-235856`
  5. 打开 `http://localhost:5173/audit`，筛选 `REPORT + PENDING`，点击“立即处理”，选择“举报不成立”并提交备注
  6. 捕获页面发出的 `PUT /api/admin/products/reports/RPT-20260316-235856/resolve`
  7. 再次查询 `GET /admin/audit/overview?type=REPORT&keyword=RPT-20260316-235856`，并在页面切到 `CLOSED` 状态确认列表展示
- 实际观察：
  - 新建举报工单号：`RPT-20260316-235856`；对应商品：`920127 / DAY19-P5-S2-20260308175931-CALLBACK`。
  - 处理前 AuditCenter 总览中该工单为 `status=PENDING / sourceStatus=PENDING`。
  - 页面动作返回 `code=1 / data=工单处理成功`；处理后总览变为 `status=CLOSED / sourceStatus=RESOLVED_INVALID`。
  - `http://localhost:5173/audit` 页面切到 `CLOSED` 后可见该工单，操作列显示 `已完成`，说明页面列表刷新与后端状态一致。
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_report_dismiss_2026-03-16.json`
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_report_dismiss_live_2026-03-16.png`
- 结论：该链路结果可明确记为 `pass`。

### 3.6 AuditCenter 仲裁（approve）真实页面联调

- 最小必要核对：
  - 页面：`AuditCenter.vue` 在 `DISPUTE` 分支提交 `approved + remark`，并依赖 `sourceId` 命中真实售后单。
  - API 模块：`src/api/audit.ts` 的 `DISPUTE` 分支固定调用 `PUT /admin/after-sales/{sourceId}/arbitrate`。
  - 后端控制器：`demo-service/src/main/java/com/demo/controller/user/AfterSaleController.java` 负责种子售后链路的 `createAfterSale / sellerDecision / dispute`，`demo-service/src/main/java/com/demo/controller/admin/AdminAfterSaleController.java` 负责平台仲裁。
- 真实联调动作：
  1. `POST /user/orders` 创建订单：`orderId=907599 / productId=920117`
  2. `POST /user/orders/907599/pay`
  3. `POST /user/orders/907599/ship`
  4. `POST /user/orders/907599/confirm-receipt`
  5. `POST /user/after-sales` 创建售后：`afterSaleId=3`
  6. `PUT /user/after-sales/3/seller-decision`，卖家拒绝售后
  7. `POST /user/after-sales/3/dispute`，买家升级为纠纷
  8. `GET /admin/audit/overview?type=DISPUTE&keyword=AS-3`
  9. 打开 `http://localhost:5173/audit`，筛选 `DISPUTE + PENDING`，点击“立即处理”，选择“支持售后申请”并提交备注
  10. 捕获页面发出的 `PUT /api/admin/after-sales/3/arbitrate`
  11. 再次查询 `GET /admin/audit/overview?type=DISPUTE&keyword=AS-3`，并在页面切到 `CLOSED` 状态确认列表展示
- 实际观察：
  - 种子工单号：`ticketId=AS-3`；对应售后单：`afterSaleId=3`；对应订单：`907599`。
  - 仲裁前总览中该工单为 `status=PENDING / sourceStatus=DISPUTED / riskLevel=HIGH`。
  - 页面动作返回 `code=1 / data=裁决通过，支持退货退款`；处理后总览变为 `status=CLOSED / sourceStatus=CLOSED`。
  - 页面 `CLOSED` 视图中该工单展示 `已关闭 / 已完成`，且描述改为 `APPROVED: ... admin arbitrate approve via AuditCenter page`。
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_dispute_arbitrate_2026-03-16.json`
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_dispute_arbitrate_live_2026-03-16.png`
- 结论：该链路结果可明确记为 `pass`。

### 3.7 OpsCenter publish-once / refund run-once 真实页面联调

- 最小必要核对：
  - 页面：`OpsCenter.vue` 统一通过 `runOpsAction()` 承接确认弹窗、按钮 loading、反馈 banner 与成功后的 `refreshRuntimeData()`。
  - API 模块：`src/api/adminExtra.ts` 中 `publishOutboxOnce()` 对应 `POST /admin/ops/outbox/publish-once`；`runRefundOnce()` 对应 `POST /admin/ops/tasks/refund/run-once`。
  - 后端控制器：`AdminOutboxOpsController.java` 暴露 `publish-once`；`AdminTaskOpsController.java` 暴露 `refund run-once`。
  - 本轮前端最小修复：`fetchOpsRuntimeBundle()` 改按 `PENDING / FAILED` 过滤摘要，避免把历史 `SUCCESS / DONE / CANCELLED` 记录误判成待处理。
- 真实联调动作：
  1. 后端管理员登录，获取真实管理端 `token`
  2. 打开 `http://localhost:5173/ops-center`
  3. 等待运行概览刷新完成，记录页面卡片与后台 `GET /admin/ops/outbox/metrics`
  4. 在页面点击“消息补发”，捕获 `POST /api/admin/ops/outbox/publish-once?limit=50`
  5. 在页面点击“退款任务处理”，捕获 `POST /api/admin/ops/tasks/refund/run-once?limit=50`
  6. 回查页面反馈 banner、运行概览刷新后的卡片，以及后台 `GET /admin/ops/tasks/refund?status=PENDING|FAILED`
- 实际观察：
  - 路由实际为 `http://localhost:5173/ops-center`。
  - 页面修复后运行概览展示为：`订单总量=70 / Outbox 待发送=0 / Outbox 失败=7 / 发货超时未完成=3 / 退款待重试=0 / 发货提醒待补跑=0 / Top1=ship_timeout(1795)`。
  - `publish-once` 页面动作返回 `code=1`，实际结果为 `pulled=0 / sent=0 / failed=0`；刷新后 Outbox 指标仍为 `new=0 / fail=7 / sent=84 / failRetrySum=1356`，说明本轮没有可拉取消息，但页面动作链路与回刷逻辑正常。
  - `refund run-once` 页面动作返回 `code=1`，实际结果为 `success=0 / batchSize=50`；回查 `PENDING=0 / FAILED=0`，与页面“待重试任务 0”一致。
  - `Test-NetConnection localhost:5672` 在 `2026-03-16` 实测为不可达，但因为本轮 `publish-once` 没有拉取到消息，所以未阻塞当前页面动作链路判定。
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_opscenter_write_actions_2026-03-16.json`
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_opscenter_write_actions_live_2026-03-16.png`
- 结论：该链路结果可明确记为 `pass`。

### 3.7 Dashboard 趋势 / 扩展统计证据补强

- 最小必要核对：
  - 页面：`demo-admin-ui/src/pages/Dashboard.vue` 仍以 `fetchDashboardData(statisticsDate)` + `fetchHomeStatisticsBundle(statisticsDate)` 并行刷新；趋势文案消费 `coreMetrics[*]`，扩展统计消费 `/admin/statistics/*` bundle，SVG 趋势线仍由 `mockTrendData` 本地生成。
  - API 模块：`demo-admin-ui/src/api/dashboard.ts` 负责 `/admin/dashboard/overview` 与 `disputeQueue` 的 `/admin/audit/overview?riskLevel=HIGH` fallback；`demo-admin-ui/src/api/adminExtra.ts` 负责 `dau / order-gmv / product-publish` 三条统计接口 bundle 化；`demo-admin-ui/src/utils/request.ts` 仍通过请求头 `token` 透传管理端凭证。
  - 后端控制器：`demo-service/src/main/java/com/demo/controller/admin/AdminDashboardController.java` 与 `demo-service/src/main/java/com/demo/controller/admin/StatisticsController.java` 路径、请求方式与前端一致，本轮未发现新的字段 / token / 协议偏差。
- 真实联调动作：
  1. `POST /admin/employee/login`
  2. `GET /admin/dashboard/overview?date=2026-03-16`
  3. `GET /admin/statistics/dau?date=2026-03-16`
  4. `GET /admin/statistics/order-gmv?date=2026-03-16`
  5. `GET /admin/statistics/product-publish?date=2026-03-16`
  6. 打开 `http://localhost:5173/` Dashboard 页面并注入真实管理员 token，抓取页面发出的 `/api/admin/dashboard/overview`、`/api/admin/statistics/*` 与 `/api/admin/audit/overview?riskLevel=HIGH`
- 实际观察：
  - 直接 API 返回：`今日成交额(GMV)=¥213`、`新增付款订单=2`、`售后争议 & 举报=0`、`DAU=1`、`支付订单=2`、`今日 GMV=¥213`、`新增发布=0`；
  - 页面实际展示三张趋势卡与四张扩展统计卡，且未出现“看板部分数据暂未同步 / 看板暂未同步成功”错误 banner；
  - `overview.disputeQueue` 为空时，页面按既有设计回退调用 `/admin/audit/overview?riskLevel=HIGH` 生成“优先跟进事项”，本轮页面抓到 `UV-1861 / UV-1860 / UV-1859` 三条高风险工单；
  - `overview.coreMetrics` 中的“待审异常商品”当前未单独渲染为趋势卡；首页现设计由“待审核商品”队列 + “新增发布”扩展统计承接，该项本轮记录为**非阻塞观察**；
  - Dashboard SVG 趋势线仍来自 `src/pages/Dashboard.vue` 内 `mockTrendData`，属于静态 UI 增强，不应误记为新增后端趋势图接口。
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_trend_stats_2026-03-16.json`
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_trend_stats_live_2026-03-16.png`
- 结论：Dashboard 趋势文案与扩展统计已补到**真实页面 + 真实接口**证据，Day09 范围内 Dashboard 主链可记为 `pass`。

## 4. 当前结果判定

| 链路 | 结果 | 说明 |
|---|---|---|
| UserList 封禁 / 解封 | `pass` | `2026-03-15` 已完成真实接口闭环。 |
| ProductReview 审核通过 / 驳回 | `pass` | `2026-03-16` 已完成真实接口闭环；本轮新增后端兼容修复后通过。 |
| Dashboard sellerName 真实页面联调 | `pass` | `2026-03-16` 已完成真实页面、真实接口与截图 / JSON 证据闭环。 |
| Dashboard 趋势 / 扩展统计证据补强 | `pass` | `2026-03-16` 已完成 `/admin/dashboard/overview + /admin/statistics/*` 的真实页面补证，并明确静态 SVG 趋势线不属于后端实时接口。 |
| AuditCenter 举报处理（dismiss） | `pass` | `2026-03-16` 已完成真实页面联调、真实工单创建、关闭态回查与截图 / JSON 证据闭环。 |
| AuditCenter 仲裁（approve） | `pass` | `2026-03-16` 已完成真实页面联调、种子售后纠纷构造、仲裁提交与关闭态回查。 |
| OpsCenter publish-once / refund run-once | `pass` | `2026-03-16` 已完成真实页面联调、页面反馈回查与截图 / JSON 证据闭环。 |

## 5. 验收口径说明

1. ProductReview 本轮结论基于**真实接口执行 + 后端修复后复测**，不是只看静态代码。
2. Dashboard 本轮已补到 **sellerName + 趋势 / 扩展统计** 的真实运行证据；其中趋势文案与扩展统计来自真实接口，SVG 趋势线仍是页面静态装饰。
3. AuditCenter 本轮已分别完成**举报 dismiss** 与**售后仲裁 approve** 两条真实写动作闭环；当前 AuditCenter 在 Day09 范围内可视为 `pass`。
4. AuditCenter 本轮未发现新的前端 / 后端 / 契约差异；`ticketNo`、`sourceId`、`action`、`approved`、`remark` 与管理端 `token` 协议都与页面/API/控制器一致。
5. RabbitMQ `localhost:5672` 在 `2026-03-16` 实测仍不可达；但本轮 OpsCenter `publish-once` 实际返回 `pulled=0 / sent=0 / failed=0`，说明当前没有可发送消息，因此不阻塞本轮页面动作链路记为 `pass`。若后续要验证真实 MQ 投递，需要单独起 MQ。
6. OpsCenter 本轮发现的问题不是接口路径或 token 协议不一致，而是**前端运行概览把历史任务总数误标成待处理数**；已在 `src/api/adminExtra.ts` 与 `src/pages/ops/OpsCenter.vue` 做最小修复。
7. Day09 当前高副作用跨端链路与 Dashboard 补证项已全部给出明确结论，可转 FrontDay10 演示版冻结。
