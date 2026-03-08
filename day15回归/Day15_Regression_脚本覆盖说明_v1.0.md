# Day15 回归脚本覆盖说明 v1.0

- 文档路径：`day15回归/Day15_Regression_脚本覆盖说明_v1.0.md`
- 对应脚本：
1. `day15回归/Day15_Regression.postman_collection.json`
2. `day15回归/Day15_Local_Regression.postman_environment.json`
- 数据准备脚本：`day15回归/Day15_Regression_Prepare.sql`

## 1. 回归前置条件
1. 已执行 `day15回归/Day15_Regression_Prepare.sql`，并生成两笔夹具订单：
   - `DAY15-REG-PAID-SHIP`
   - `DAY15-REG-PAID-TIMEOUT`
2. 应用配置满足 Day15 演示口径：
   - `logistics.provider=mock`
   - `order.ship-timeout.penalty.enabled=true`
   - `order.refund.accounting.enabled=true`
   - `order.ship-reminder.enabled=true`
   - `order.notice.ship-reminder-enabled=true`
   - `order.notice.ship-timeout-cancel-enabled=true`
   - `order.notice.refund-success-enabled=true`
3. 服务已启动（默认 `http://localhost:8080`）。

## 2. 环境脚本（environment）详细说明
文件：`day15回归/Day15_Local_Regression.postman_environment.json`

核心变量：
1. `baseUrl`：接口基地址。
2. `buyer_username` / `buyer_password`：买家登录凭据。
3. `seller_username` / `seller_password`：卖家登录凭据。
4. `admin_loginId` / `admin_password`：管理员登录凭据。
5. `fixture_order_no_paid_ship`：发货主链路订单号。
6. `fixture_order_no_paid_timeout`：超时取消主链路订单号。

运行期变量（由脚本自动写入）：
1. `token_buyer` / `token_seller` / `token_admin`：三端 token。
2. `buyerId` / `sellerId` / `adminId`：登录后用户 ID。
3. `orderId_paid_ship` / `orderId_paid_timeout`：夹具订单 ID。
4. `reminder_task_id_timeout`：超时订单提醒任务 ID（用于手动触发）。
5. `refund_task_id_timeout`：退款任务 ID（用于状态断言）。
6. `tracking_no_used`：本次发货运单号（动态生成避免冲突）。
7. `refund_tx_count_timeout`：退款流水条数快照（用于幂等复验）。
8. `runTag`：本次运行标识（日志追踪辅助）。

## 3. 集合脚本（collection）详细覆盖
文件：`day15回归/Day15_Regression.postman_collection.json`

### 3.1 `01-Auth`
覆盖目标：认证可用，拿到三端 token。

1. `Buyer Login`
- 校验：HTTP 200、`code==1`、`token_buyer` 写入成功。
2. `Seller Login`
- 校验：HTTP 200、`code==1`、`token_seller` 写入成功。
3. `Admin Login`
- 校验：HTTP 200、`code==1`、`token_admin` 写入成功。

### 3.2 `02-Fixture-Discover`
覆盖目标：自动发现夹具订单与任务 ID，避免手填。

1. `Seller List Paid Orders And Capture Fixture IDs`
- 校验：能查到两笔夹具订单。
- 输出：`orderId_paid_ship`、`orderId_paid_timeout`。
2. `Admin List Ship Reminder Tasks (Paid Ship)`
- 校验：发货单的提醒任务不少于 3 条（H24/H6/H1）。
3. `Admin List Ship Reminder Tasks (Paid Timeout) And Pick Task`
- 校验：超时单提醒任务不少于 3 条。
- 输出：`reminder_task_id_timeout`。
4. `Admin Trigger Reminder Task Now`
- 校验：任务触发接口成功，回包 `taskId` 一致。
5. `Admin List Ship Timeout Task (Paid Timeout)`
- 校验：超时任务存在。
- 输出：`refund_task_id_timeout`（若已创建）。

### 3.3 `03-Ship-And-Logistics`
覆盖目标：`paid -> shipped` 发货链路 + mock 轨迹展示 + 发货幂等。

1. `Seller Ship Paid Order`
- 校验：发货成功（含幂等文本兼容）。
- 输出：`tracking_no_used`。
2. `Buyer Get Order Detail (Expect Shipped)`
- 校验：订单状态为 `shipped`，运单号存在。
3. `Buyer Get Logistics (Mock Trace)`
- 校验：`provider=mock`，轨迹节点数量 >= 1。
4. `Seller Repeat Ship (Idempotent)`
- 校验：重复发货返回幂等语义，不重复推进状态。

### 3.4 `04-Reminder-Notify`
覆盖目标：提醒任务执行 + 卖家可见提醒 + 已读链路。

1. `Admin Run Ship Reminder Once`
- 校验：run-once 回包含 `success`。
2. `Admin Check Reminder Tasks (Timeout Order)`
- 校验：任务状态出现 `SUCCESS` 或 `CANCELLED`。
3. `[Seller] List Messages For Timeout Order (Reminder)`
- 校验：卖家会话中出现提醒消息（关键字匹配）。
4. `[Seller] Mark Timeout Conversation As Read`
- 校验：标记已读接口返回成功。
5. `[Seller] List Messages After Read (All Read)`
- 校验：该订单会话消息全部为已读。

### 3.5 `05-Timeout-Refund-Closure`
覆盖目标：超时取消、退款任务、钱包记账、通知、处罚、退款幂等复验。

1. `Admin Run Ship Timeout Once`
- 校验：run-once 回包含 `success`。
2. `Admin Verify Cancelled Order Reason = ship_timeout`
- 校验：超时单已进入 `cancelled`，且 `cancelReason=ship_timeout`。
3. `Admin Run Refund Once`
- 校验：退款执行回包含 `success`。
4. `Admin Verify Refund Task SUCCESS`
- 校验：对应 `order_refund_task` 已出现 `SUCCESS`。
5. `Buyer Wallet Transactions Contains ORDER_REFUND`
- 校验：钱包流水存在 `ORDER_REFUND` 且 remark 命中该订单。
- 输出：`refund_tx_count_timeout`。
6. `[Buyer] List Messages For Timeout Order (Cancel + Refund)`
- 校验：买家消息同时包含“超时取消通知”和“退款成功通知”。
7. `Admin Verify Seller Violation Created (ship_timeout)`
- 校验：卖家违规记录中存在 `ship_timeout`（或描述命中“超时未发货”）。
8. `Admin Run Refund Once Again (Idempotent Guard)`
- 校验：重复执行退款任务接口正常返回。
9. `Buyer Wallet Refund Count Stable After Re-Run`
- 校验：重复执行后退款流水条数不增长（幂等成立）。

### 3.6 `06-Ops-Sanity`
覆盖目标：运维查询接口健康度与数据可观测性。

1. `Admin List Ship Timeout Tasks By Status`
- 校验：返回数组结构。
2. `Admin List Refund Tasks By Status`
- 校验：返回数组结构。
3. `Admin List Ship Reminder Tasks By Status`
- 校验：返回数组结构。

## 4. 与 Day15 冻结文档的覆盖映射
对应文件：`day15回归/Day15_Scope_Freeze_v1.0.md`

1. 第 4 章接口契约：
- 覆盖 `POST /user/orders/{orderId}/ship`
- 覆盖 `GET /user/orders/{orderId}/logistics`
- 覆盖 `GET /user/orders/sell?status=paid`
2. 第 5 章状态机：
- 覆盖 `paid -> shipped`
- 覆盖 `paid -> cancelled(ship_timeout)`
3. 第 7 章超时与补偿：
- 覆盖超时扫描执行
- 覆盖退款任务推进与钱包记账
- 覆盖卖家违规处罚落库
4. 第 8 章物流同步展示：
- 覆盖 mock 轨迹查询展示
5. 第 12 章通知机制：
- 覆盖提醒通知
- 覆盖超时取消通知
- 覆盖退款结果通知

## 5. Newman 运行命令
在项目根目录执行：

```bash
newman run day15回归/Day15_Regression.postman_collection.json -e day15回归/Day15_Local_Regression.postman_environment.json --bail
```

如需导出报告：

```bash
newman run day15回归/Day15_Regression.postman_collection.json -e day15回归/Day15_Local_Regression.postman_environment.json -r cli,json --reporter-json-export day15回归/Day15_Newman_Report.json
```

## 6. 说明
1. 本回归默认按 mock 物流方案执行，不依赖第三方物流 API。
2. 若你关闭了处罚或通知开关，对应断言会失败（属于配置导致，不是脚本问题）。
3. 当前脚本是“全链路后端回归”，不依赖前端页面。
