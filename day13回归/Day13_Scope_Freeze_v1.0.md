# Day13 范围冻结：订单 / 沟通 / 售后 / 商品状态 / 后台 / 统计 / 支付与余额（Scope Freeze）

- 项目：二手交易平台（secondhand2 / com.demo）
- 模块：Day13 交易闭环增强（订单管理 + 站内沟通 + 售后 + 后台管理 + 统计 + 支付/余额/积分）
- 文档版本：v1.0（首次冻结｜工程口径对齐版）
- 冻结日期：2026-01-31
- 目标：把 Day13 “做什么 / 不做什么 / 规则口径 / 接口契约 / 数据结构 / 验收标准”一次写死，避免实现过程反复改口径导致返工。

---

## 0. 边界决策（已敲定）
本 Day13 的功能边界以以下开关为准（YES/NO）。**除非升级本文档版本号，否则实现不得越界**。

1. 订单与商品关系：**YES（固定一单一商品，quantity=1）**
2. 商品锁定方式：**YES（创建订单即将商品从 `on_sale` 原子更新为 `sold`）**
3. 订单状态口径：**YES（沿用现有 `OrderStatus`：pending/paid/shipped/completed/cancelled，不新增 “退款中/已退款”）**
4. 支付渠道：**NO（不接真实支付宝/微信 SDK；仅提供“统一支付下单 + 回调接口 + 假支付模拟”打通状态流转）**
5. 支付回调安全：**YES（验签/幂等/防重放 口径冻结；真实验签算法后续替换）**
6. 站内消息：**YES（消息存 MongoDB；消息体仅支持文本）**
7. 消息推送：**NO（不做 WebSocket 推送；提供“未读数/轮询拉取”实现异步沟通体验）**
8. 评价：**YES（沿用 Day12：买家→卖家，一次性 1~5 星 + 文字；新增基础防刷口径）**
9. 售后类型：**YES（仅退货退款；不做换货）**
10. 售后触发时机：**YES（确认收货后 7 天内可申请；卖家 48 小时内需响应；协商失败平台仲裁）**
11. 商品审核：**YES（敏感词检测：本地词库；图片违规识别：NO，仅占位）**
12. 商品编辑后流程：**YES（编辑后统一回到 `under_review`，需重新审核）**
13. 后台用户管理：**YES（封禁/解封使用 users.status=active/banned；支持导出 CSV）**
14. 后台订单监控：**YES（管理员可全局查看订单并标记异常）**
15. 统计面板：**YES（仅提供后台聚合 API；不做前端可视化页面）**
16. 余额系统：**YES（余额查询/流水/提现申请；提现仅记录，不对接真实出金）**
17. 积分系统：**YES（仅记账：订单完成加积分；不做复杂规则与兑换商城）**

> 说明：本冻结文档以你工程现有口径为准（例如：订单接口路径 `/user/orders`、订单超时关单配置 `order.timeout.*`、商品状态枚举 `ProductStatus` 等）。

---

## 1. 术语与对象定义
- **订单（Order）**：买家对某个商品发起购买后形成的交易记录，包含金额、收货地址快照、状态、时间戳等。
- **订单明细（OrderItem）**：订单关联的商品信息。本项目冻结为“一单一商品”，因此 `order_items` 仅保存 1 行。
- **商品锁定**：订单创建成功后，商品状态从 `on_sale` 原子更新为 `sold`，用以防止重复下单/重复购买。
- **站内消息（Message）**：买卖双方围绕某笔订单的异步沟通消息，存 MongoDB。
- **售后（AfterSale）**：退货退款申请流程与平台仲裁记录（与订单状态机解耦，不新增订单“退款中/已退款”状态）。
- **异常订单标记（OrderFlag）**：后台对订单风险/异常的备注与标记（不影响订单主状态）。
- **DAU（日活）**：每日登录或浏览的去重用户数（以“访问日志/接口触达”作为口径，见第 2.11）。
- **GMV**：统计周期内“已支付且未取消”的订单金额累计（冻结以“订单 pay_time 落在统计日”作为口径）。

---

## 2. 业务规则冻结（核心口径）

## 2.1 订单生命周期状态机（唯一口径）
状态枚举与 dbValue（与你工程 `com.demo.enumeration.OrderStatus` 对齐）：
- `pending`：待付款
- `paid`：已付款待发货
- `shipped`：已发货待收货
- `completed`：已完成
- `cancelled`：已取消

允许的状态流转（必须严格限制）：
1. `pending` → `paid`（支付成功）
2. `pending` → `cancelled`（买家取消 / 超时关单）
3. `paid` → `shipped`（卖家发货）
4. `shipped` → `completed`（买家确认收货）

禁止的流转（出现即业务异常）：
- `paid` 之后禁止走“取消”（退款/售后走 AfterSale，不改变订单主状态）
- `cancelled` 为终态，不允许任何进一步操作
- `completed` 为终态（售后申请不改变主状态）

## 2.2 订单创建（买家下单）
接口：见第 5.1。

**前置条件（必须全部满足）：**
1. 商品必须存在且未删除
2. 商品状态必须为 `on_sale`
3. 当前登录用户不得为商品发布者（buyer != seller）
4. 收货地址 `shippingAddress` 必须非空（最小长度 5，最大长度 200）

**核心动作（强制冻结）：**
- 创建订单前必须“原子锁定商品”：
  - DB 更新：`UPDATE products SET status='sold' WHERE id=? AND status='on_sale' AND is_deleted=0`
  - 若更新行数=0：判定为“已被购买/不可购买”，返回固定错误（见第 7 节）
- 订单落库：
  - `status` 固定为 `pending`
  - `order_no` 生成规则冻结为：`yyyyMMddHHmmss + (buyerId%10000) + 4位随机数`（与你现有实现一致）
  - `create_time=NOW()`，`update_time=NOW()`
- 明细落库（order_items）：
  - 固定 `quantity=1`
  - `price` 为商品当前价格快照

**幂等与并发：**
- 同一商品的并发下单：以“锁定商品更新行数”作为唯一判定，**只允许一个订单成功**。

## 2.3 支付（统一支付 + 回调）
Day13 目标是“把支付状态流转打通”，不接真实 SDK（见 0.4）。

**支付统一口径：**
- 买家发起支付（`POST /user/orders/{orderId}/pay`）只做订单状态更新：`pending -> paid`（你现有实现已支持幂等）
- 额外新增“模拟回调接口”（见第 5.6），用于未来接入真实支付宝/微信的 webhook/callback：
  - 回调到达后必须按 `out_trade_no/orderNo` 或 `orderId` 定位订单
  - 幂等：重复回调不得重复变更状态/重复记账
  - 安全：必须校验签名字段（Day13 先做占位验签，详见 5.6）

**支付成功后的库存口径：**
- 由于二手商品“一件一卖”，库存扣减在“下单锁定”时已完成（商品状态置为 `sold`）。
- 因此“支付成功”不再做库存扣减，仅保持锁定不释放。

## 2.4 取消订单与超时关单（待付款自动关闭）
### 2.4.1 买家取消（pending 才能取消）
口径与你现有实现一致：
- 仅允许 `pending -> cancelled`
- 取消成功后必须释放商品：将该订单关联商品从 `sold` 还原为 `on_sale`
- `cancel_reason`：
  - request.reason 为空时固定为 `buyer_cancel`
  - 否则使用 trim 后的字符串（最大 100）

### 2.4.2 超时关单（定时任务）
你工程已存在 Job（`OrderTimeoutJob`），Day13 冻结如下：
- 配置项（`application.yml`）：
  - `order.timeout.pending-minutes` 默认 15
  - `order.timeout.fixed-delay-ms` 默认 60000（每分钟）
  - `order.timeout.batch-size` 默认 200
- 关单规则：
  - 查询 `pending` 且 `create_time <= deadline` 的订单
  - 执行 `pending -> cancelled`，并固定 `cancel_reason='timeout'`
  - 关单成功后释放商品（sold → on_sale）

## 2.5 发货与确认收货
### 2.5.1 卖家发货（paid → shipped）
- 仅卖家本人可操作
- 订单状态必须为 `paid`
- 必填字段：`shippingCompany`、`trackingNo`（最大长度 50）
- 可选字段：`shippingRemark`（最大长度 200）
- 需要落库：`ship_time=NOW()`
- 幂等：若已 `shipped/completed`，接口返回成功提示（不抛异常）

### 2.5.2 买家确认收货（shipped → completed）
- 仅买家本人可操作
- 订单状态必须为 `shipped`
- 需要落库：`complete_time=NOW()`
- 幂等：若已 `completed`，接口返回成功提示（不抛异常）

## 2.6 交易沟通（站内消息，MongoDB）
### 2.6.1 会话模型冻结（以订单为会话维度）
- 会话维度：`orderId`（一笔订单一个会话）
- 会话参与者：订单 `buyerId` 与 `sellerId`

### 2.6.2 发消息规则
**前置条件：**
1. 订单必须存在
2. 当前用户必须是该订单买家或卖家
3. 订单状态不得为 `cancelled`（取消后不允许再发送消息，避免骚扰）

**字段规则：**
- `content`：最小长度 1；最大长度 500
- `clientMsgId`：客户端生成的幂等键（UUID/雪花均可），用于防止重复发送

**防刷/频控（基础版，Day13 必须做）：**
- 单用户单会话：1 秒内最多 3 条（可用 Redis/本地滑窗，Redis 未启用时可退化为 DB 校验）
- 触发频控时提示语固定（见第 7 节）

### 2.6.3 MongoDB 存储冻结
Collection：`order_messages`

索引（必须建）：
- `idx_order_time(orderId, createTime desc)`
- `uniq_order_clientMsg(orderId, fromUserId, clientMsgId)`（用于幂等）
- `idx_to_read(toUserId, read, createTime desc)`（用于未读数/拉取）

消息文档字段（冻结最小集）：
- `_id`：ObjectId
- `orderId`：Long
- `fromUserId`：Long
- `toUserId`：Long
- `content`：String
- `read`：Boolean（默认 false）
- `createTime`：Date

## 2.7 评价系统（买家评分卖家，五星 + 文本 + 防刷）
Day13 评价沿用 Day12 的核心规则（订单完成后一次性评价），并新增“基础防刷”冻结：

**前置条件：**
1. 订单存在且当前用户是 buyer
2. 订单状态必须为 `completed`
3. 同一订单仅允许评价一次（DB 唯一键兜底）

**字段规则：**
- `rating`：整数 1~5
- `content`：最小长度 10；最大长度 500
- `isAnonymous`：true/false（匿名仅影响展示，不影响风控追溯）

**基础防刷（Day13 必须做）：**
- 同一买家：24 小时内最多创建 20 条评价（以 reviews.create_time 为准）
- 频控命中时提示语固定（见第 7 节）

## 2.8 售后服务（退货退款 + 纠纷）
### 2.8.1 售后与订单状态解耦（强制冻结）
- 售后不新增订单主状态，不修改 `orders.status`（保持 `completed`）
- 售后状态全部记录在 `after_sales`（见第 6.3）

### 2.8.2 退货申请规则（买家）
**前置条件：**
1. 订单必须存在
2. 当前用户必须为 buyer
3. 订单状态必须为 `completed`
4. `complete_time` 距当前时间不超过 7 天
5. 同一订单仅允许存在 1 条进行中的售后（终态后可再次申请：NO，Day13 禁止）

**提交字段：**
- `reason`：必填，2~200
- `evidenceImages`：可选，最多 3 张，存 URL（Day13 不做上传，只接收前端上传后的 URL）

### 2.8.3 卖家响应规则（48 小时）
- 卖家必须在 48 小时内处理：同意 / 拒绝
- 超时未处理：自动进入“平台介入”队列（Day13 先提供后台接口手动介入，不做自动流转）

### 2.8.4 纠纷提交与平台裁决
- 当卖家拒绝，买家可提交纠纷申请（见第 5.4）
- 平台客服依据规则做裁决：同意退款 / 驳回

> Day13 冻结：退款只做“业务结果落库 + 余额记账（可选）”，不对接真实原路退回。

## 2.9 商品状态管理与审核
与你工程现有 `ProductStatus` 对齐：
- `under_review`：审核中
- `on_sale`：上架/在售
- `off_shelf`：下架
- `sold`：已售（订单创建锁定即售出）

冻结规则：
1. 商品发布后默认 `under_review`
2. 审核通过 → `on_sale`
3. 审核驳回 → `off_shelf`（保留驳回原因）
4. 已发布商品允许编辑（除 `sold` 外），但编辑后必须回到 `under_review` 并清空历史驳回原因
5. 手动下架：仅允许 `under_review/on_sale -> off_shelf`

敏感词检测（Day13 必须做）：
- 检测字段：title/description
- 词库：本地配置/DB 表均可（实现选型不冻结）
- 命中后处理：
  - 低风险：标记 violation 记录但仍允许进入人工审核
  - 高风险：直接阻断上架，状态保持 `under_review`，需管理员审核

## 2.10 后台管理模块
### 2.10.1 用户管理（封禁/解封/导出）
- 封禁语义冻结：更新 `users.status`：
  - `active`：正常
  - `banned`：封禁
- 封禁后影响（Day13 必须做）：
  - `banned` 用户不得调用任何 `/user/**` 写接口（下单/支付/发消息/评价/售后申请等）
  - 读接口是否允许：Day13 冻结为 **允许读（GET）**，禁止写（POST/PUT/DELETE）
- 导出：CSV（UTF-8），字段见第 5.5

### 2.10.2 商品审核与监控
沿用现有 `/admin/products/**` 审核接口，并补充：
- 管理员可查看违规记录、对违规商品下架（off_shelf）并记录原因

### 2.10.3 订单监控
管理员能力冻结：
- 全局分页查询所有订单（可按状态/时间筛选）
- 对异常订单进行标记（不改订单主状态）

## 2.11 数据统计面板（后台聚合 API）
### 2.11.1 DAU 口径冻结
Day13 冻结为“接口触达去重”（实现简单且可回归）：
- 统计来源：记录用户访问日志（建议落库 `user_activity_daily` 或 Redis 计数；实现选型不冻结）
- 触达定义：用户调用任一 `/user/**` GET 接口视为当天活跃（不含静态资源）

### 2.11.2 商品发布量
- 口径：products.create_time 落在统计日的记录数
- 支持按 category 分解

### 2.11.3 成交订单量与 GMV
- 成交订单量：统计日内 `pay_time` 非空的订单数（且 status != cancelled）
- GMV：统计日内 `pay_time` 非空订单的 `total_amount` 求和

## 2.12 余额 / 提现 / 积分
### 2.12.1 余额系统
- 每个用户一份钱包：`user_wallets`
- 余额变动必须记流水：`wallet_transactions`
- 提现只记录申请：`withdraw_requests`（不做真实打款）

### 2.12.2 积分系统
- 积分采用“记账表”方式：`points_ledger`
- 积分发放冻结：订单完成（`completed`）后给 buyer + seller 各 +N（N 默认 1，可配置）
- 积分不支持兑换/抵扣支付（Day13 不做）

---

## 3. 不做什么（Non-goals，明确禁止做）
Day13 **不实现**：
- 真实支付宝/微信 SDK 接入、真实验签算法、真实退款原路退回（仅模拟回调打通）
- WebSocket/SSE 实时推送（仅提供未读数 + 轮询拉取）
- 站内消息图片/语音/文件（仅文本）
- 复杂反作弊（设备指纹、IP 画像、内容相似度、黑白名单系统）
- 售后换货、部分退款、多次售后、退款后订单状态扩展（保持订单状态机不膨胀）
- 统计面板前端页面与大屏（仅后台 API 输出数据）
- 提现真实出金、绑卡鉴权、风控限额引擎（仅记录与限额校验）

---

## 4. 权限与安全冻结（与当前拦截器口径一致）
### 4.1 用户侧
- 所有 `/user/**` 接口：必须登录（header `authentication: <token>`，与你 `application.yml` 对齐）
- `users.status=banned`：
  - 禁止所有写接口（POST/PUT/DELETE）
  - 允许读接口（GET）

### 4.2 后台侧
- 所有 `/admin/**` 接口：必须后台登录（header `token: <adminToken>`，与你 `application.yml` 对齐）

### 4.3 数据安全
- 订单详情查询：仅 buyer 或 seller 可查看（你现有 SQL 已限制）
- 站内消息：仅订单参与者可读写

---

## 5. 接口契约冻结（建议直接贴进接口文档）
> 返回包装沿用你项目统一 `Result<T>` 与 `com.demo.result.PageResult<T>`  
> 成功：`code = 1`；失败：`code = 0`

## 5.1 订单接口（用户侧，已存在口径冻结）
### 5.1.1 创建订单
- **POST** `/user/orders`
- Request（JSON）`CreateOrderRequest`：

```json
{
  "productId": 48,
  "shippingAddress": "上海市浦东新区XX路XX号 2-301"
}
```

- Response：`Result<CreateOrderResponse>`
  - `orderId`
  - `orderNo`
  - `status`（pending）
  - `totalAmount`
  - `createTime`

### 5.1.2 支付订单
- **POST** `/user/orders/{orderId}/pay`
- Response：`Result<String>`（固定 msg：见第 7 节）

### 5.1.3 取消订单
- **POST** `/user/orders/{orderId}/cancel`
- Request（JSON，可选）：

```json
{ "reason": "不想要了" }
```

### 5.1.4 卖家发货
- **POST** `/user/orders/{orderId}/ship`
- Request（JSON）`ShipOrderRequest`：

```json
{
  "shippingCompany": "顺丰",
  "trackingNo": "SF1234567890",
  "remark": "请注意查收"
}
```

### 5.1.5 买家确认收货
- **POST** `/user/orders/{orderId}/confirm-receipt`

### 5.1.6 我买到的订单（分页）
- **GET** `/user/orders/buy?page=1&pageSize=10&status=pending&sortField=createTime&sortOrder=desc`

### 5.1.7 我卖出的订单（分页）
- **GET** `/user/orders/sell?page=1&pageSize=10&status=paid`

### 5.1.8 订单详情
- **GET** `/user/orders/{orderId}`

## 5.2 站内消息（用户侧）
### 5.2.1 发送消息
- **POST** `/user/messages/orders/{orderId}`
- Request（JSON）：

```json
{
  "toUserId": 10002,
  "clientMsgId": "3b2e2a5b-2a0b-4fd0-b6f7-86b1d0c41f3a",
  "content": "你好，我已付款，请尽快发货～"
}
```

- Response：`Result<MessageDTO>`

### 5.2.2 拉取订单会话消息（分页）
- **GET** `/user/messages/orders/{orderId}?page=1&pageSize=20`
- Response：`Result<PageResult<MessageDTO>>`（按 createTime asc 返回，便于聊天展示）

### 5.2.3 未读数
- **GET** `/user/messages/unread-count`
- Response：`Result<Long>`

### 5.2.4 标记已读（按订单）
- **PUT** `/user/messages/orders/{orderId}/read`
- Response：`Result<String>`

## 5.3 评价（沿用 Day12，接口口径保持不变）
- **POST** `/user/reviews`
- **GET** `/user/reviews/mine`
- **GET** `/user/market/products/{productId}/reviews`

> Day13 仅在服务端新增“基础防刷”校验，不新增新接口。

## 5.4 售后（用户侧 + 后台）
### 5.4.1 买家发起退货退款申请
- **POST** `/user/after-sales`
- Request（JSON）：

```json
{
  "orderId": 900003,
  "reason": "商品与描述不符",
  "evidenceImages": [
    "https://cdn.example.com/a.jpg",
    "https://cdn.example.com/b.jpg"
  ]
}
```

- Response：`Result<Long>`（afterSaleId）

### 5.4.2 卖家处理售后（同意/拒绝）
- **PUT** `/user/after-sales/{afterSaleId}/seller-decision`
- Request（JSON）：

```json
{ "approved": false, "remark": "不同意，描述一致" }
```

### 5.4.3 买家提交纠纷（平台介入）
- **POST** `/user/after-sales/{afterSaleId}/dispute`
- Request（JSON）：

```json
{ "content": "协商失败，请平台介入处理" }
```

### 5.4.4 后台裁决
- **PUT** `/admin/after-sales/{afterSaleId}/arbitrate`
- Request（JSON）：

```json
{ "approved": true, "remark": "判定退货退款成立" }
```

## 5.5 后台管理
### 5.5.1 用户封禁/解封
- **PUT** `/admin/user/{userId}/ban`
- **PUT** `/admin/user/{userId}/unban`
- Response：`Result<String>`

### 5.5.2 用户导出（CSV）
- **GET** `/admin/user/export?keyword=&startTime=&endTime=`
- Content-Type：`text/csv; charset=utf-8`
- 字段冻结（列顺序必须固定，便于审计导入）：  
  `id,username,mobile,email,nickname,status,credit_score,credit_level,create_time,update_time`

### 5.5.3 全局订单分页（后台）
- **GET** `/admin/orders?page=1&pageSize=10&status=paid&startTime=2026-01-01 00:00:00&endTime=2026-01-31 23:59:59`
- Response：`Result<PageResult<AdminOrderDTO>>`

### 5.5.4 标记异常订单
- **POST** `/admin/orders/{orderId}/flags`
- Request（JSON）：

```json
{ "type": "suspicious", "remark": "疑似刷单，待核查" }
```

## 5.6 支付回调（模拟接口）
> 统一接入层占位：未来支付宝/微信都回调到这里，由渠道字段区分。

- **POST** `/payment/callback`
- Request（JSON）：

```json
{
  "channel": "mock",
  "orderNo": "2026013112300012345678",
  "tradeNo": "MOCK_T20260131_0001",
  "amount": 88.50,
  "status": "SUCCESS",
  "timestamp": 1706666666,
  "sign": "placeholder"
}
```

冻结规则：
- `status=SUCCESS` 才触发订单 `pending -> paid`
- 幂等：重复回调返回 success（不重复更新）
- 验签：Day13 先按“sign 非空 + timestamp 在 5 分钟内”做占位校验；真实验签算法后续替换但接口字段不变

---

## 6. 数据结构冻结

## 6.1 MySQL：orders / order_items（现有字段补全冻结）
### 6.1.1 表：orders（字段最小集）
- `id` BIGINT PK
- `order_no` VARCHAR(64) NOT NULL
- `buyer_id` BIGINT NOT NULL
- `seller_id` BIGINT NOT NULL
- `total_amount` DECIMAL(10,2) NOT NULL
- `status` VARCHAR(32) NOT NULL（pending/paid/shipped/completed/cancelled）
- `shipping_address` VARCHAR(200) NOT NULL
- `shipping_company` VARCHAR(50) NULL
- `tracking_no` VARCHAR(50) NULL
- `shipping_remark` VARCHAR(200) NULL
- `ship_time` DATETIME NULL
- `pay_time` DATETIME NULL
- `complete_time` DATETIME NULL
- `cancel_time` DATETIME NULL
- `cancel_reason` VARCHAR(100) NULL
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

索引建议（必须保持）：
- `uniq_order_no(order_no)`
- `idx_buyer_time(buyer_id, create_time)`
- `idx_seller_time(seller_id, create_time)`
- `idx_status_time(status, create_time)`

### 6.1.2 表：order_items
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL
- `product_id` BIGINT NOT NULL
- `price` DECIMAL(10,2) NOT NULL
- `quantity` INT NOT NULL（冻结为 1）
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

## 6.2 MySQL：异常订单标记（新增）
表：`order_flags`
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL
- `type` VARCHAR(32) NOT NULL（如 suspicious/refund_risk/other）
- `remark` VARCHAR(200) NULL
- `created_by` BIGINT NOT NULL（adminId）
- `create_time` DATETIME NOT NULL

唯一约束（防重复刷标记）：
- `UNIQUE KEY uniq_order_type(order_id, type)`

## 6.3 MySQL：售后（新增）
表：`after_sales`
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL
- `buyer_id` BIGINT NOT NULL
- `seller_id` BIGINT NOT NULL
- `reason` VARCHAR(200) NOT NULL
- `status` VARCHAR(32) NOT NULL（冻结枚举见下）
- `seller_remark` VARCHAR(200) NULL
- `platform_remark` VARCHAR(200) NULL
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

售后状态枚举（冻结）：
- `APPLIED`：已申请，待卖家处理
- `SELLER_APPROVED`：卖家同意
- `SELLER_REJECTED`：卖家拒绝
- `DISPUTED`：已提交纠纷，待平台裁决
- `PLATFORM_APPROVED`：平台支持退款
- `PLATFORM_REJECTED`：平台驳回
- `CLOSED`：关闭（退款完成/驳回结案均可进入 CLOSED；Day13 先统一用 CLOSED 终态）

表：`after_sale_evidences`
- `id` BIGINT PK
- `after_sale_id` BIGINT NOT NULL
- `image_url` VARCHAR(500) NOT NULL
- `sort` INT NOT NULL DEFAULT 1
- `create_time` DATETIME NOT NULL

约束：
- `after_sales`：`UNIQUE KEY uniq_order(order_id)`（一单仅一个售后，Day13 强制）

## 6.4 MySQL：余额/提现/积分（新增）
表：`user_wallets`
- `user_id` BIGINT PK
- `balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00
- `update_time` DATETIME NOT NULL

表：`wallet_transactions`
- `id` BIGINT PK
- `user_id` BIGINT NOT NULL
- `biz_type` VARCHAR(32) NOT NULL（ORDER_PAY/ORDER_REFUND/WITHDRAW/ADJUST）
- `biz_id` BIGINT NULL（如 orderId）
- `amount` DECIMAL(12,2) NOT NULL（正为入账，负为出账）
- `balance_after` DECIMAL(12,2) NOT NULL
- `remark` VARCHAR(200) NULL
- `create_time` DATETIME NOT NULL

表：`withdraw_requests`
- `id` BIGINT PK
- `user_id` BIGINT NOT NULL
- `amount` DECIMAL(12,2) NOT NULL
- `status` VARCHAR(32) NOT NULL（APPLIED/APPROVED/REJECTED/PAID，Day13 可仅用 APPLIED）
- `bank_card_no` VARCHAR(32) NOT NULL（可脱敏存储：仅后四位+hash，具体实现不冻结）
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

表：`points_ledger`
- `id` BIGINT PK
- `user_id` BIGINT NOT NULL
- `biz_type` VARCHAR(32) NOT NULL（ORDER_COMPLETED）
- `biz_id` BIGINT NOT NULL（orderId）
- `points` INT NOT NULL（正为增加）
- `create_time` DATETIME NOT NULL

唯一约束（防重复发放积分）：
- `UNIQUE KEY uniq_points_biz(user_id, biz_type, biz_id)`

## 6.5 MongoDB：消息集合（新增）
见第 2.6.3（collection `order_messages` + 索引）。

---

## 7. 错误码与提示语冻结（便于回归断言）
> 项目当前多用 BusinessException + msg；Day13 也按该方式冻结“提示语口径”。

订单：
- 商品不存在：`"商品不存在"`
- 商品非在售不可下单：`"商品非在售状态，无法下单"`
- 不能购买自己商品：`"不能购买自己发布的商品"`
- 商品已被购买：`"商品已被购买或不可购买，请刷新后重试"`
- 订单不存在或无权：`"订单不存在或无权操作该订单"`
- 订单不存在或无权查看：`"订单不存在或无权查看该订单"`
- 订单已取消不可支付：`"订单已取消，无法支付"`
- 订单已支付不可取消：`"订单已支付，当前不允许取消（后续走退款/售后）"`

消息：
- 无权发送消息：`"无权在该订单中发送消息"`
- 订单已取消禁止沟通：`"订单已取消，无法发送消息"`
- 消息过频：`"发送过于频繁，请稍后再试"`

评价：
- 评价过频：`"评价过于频繁，请稍后再试"`

售后：
- 超过7天不可申请：`"已超过确认收货7天，无法申请售后"`
- 非完成订单不可售后：`"订单未完成，无法申请售后"`
- 已存在售后：`"该订单已存在售后申请"`

后台：
- 用户不存在：`"用户不存在"`
- 封禁/解封失败：`"操作失败"`

---

## 8. Day13 最小验收标准（全绿定义）
### 8.1 订单闭环
1. on_sale 商品创建订单成功：订单 pending；商品变 sold
2. pending 订单支付成功：订单变 paid（重复支付幂等成功提示）
3. pending 订单取消成功：订单 cancelled；商品释放回 on_sale（重复取消幂等成功提示）
4. paid 订单卖家发货成功：订单 shipped；写入物流字段与 ship_time（重复发货幂等）
5. shipped 订单买家确认收货成功：订单 completed；写入 complete_time（重复确认幂等）
6. 超时关单：pending 超过 15 分钟自动取消并释放商品（cancel_reason='timeout'）

### 8.2 站内消息
1. 买卖双方可在订单内互发消息，消息落 MongoDB
2. 非订单参与者发送/拉取消息被拒绝（提示语命中）
3. 取消订单后不可再发送消息（提示语命中）
4. 未读数可查询，标记已读后未读数下降

### 8.3 售后
1. completed 订单 7 天内可申请售后成功；超过 7 天失败（提示语命中）
2. 同一订单重复申请售后失败（提示语命中）
3. 卖家可同意/拒绝；拒绝后买家可提交纠纷；后台可裁决并落库状态

### 8.4 后台
1. 管理员可封禁/解封用户；封禁用户禁止写接口
2. 管理员可全局分页查询订单
3. 管理员可对订单打异常标记（幂等：同 type 不重复）

### 8.5 统计
1. 返回 DAU/发布量/成交订单量/GMV 的聚合结果，口径与第 2.11 一致

---

## 9. 回归数据约定（建议固定几条）
- 固定 buyer：`buyer01`
- 固定 seller：`seller01`
- 固定 on_sale 商品：`productId = 48`（回归下单用，确保状态为 on_sale）
- 固定订单：
  - 通过回归脚本动态创建一笔 pending 订单（用于支付/取消/消息）
  - 再创建并推进到 completed 的订单（用于评价/售后）

---

## 10. 未来扩展接口位（仅占位，不实现）
- WebSocket 推送：`/ws/messages`
- 图片消息：`msgType=image` + 对象存储上传
- 真实支付渠道：`/payment/alipay/callback`、`/payment/wechat/callback`（最终可汇聚到 /payment/callback）
- 售后物流回寄：`PUT /user/after-sales/{id}/buyer-ship-back`
- 退款原路退回：对接渠道退款 API + 退款流水

---

（文件结束）

