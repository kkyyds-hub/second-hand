# Day15 范围冻结：订单发货与物流模块（Scope Freeze）

- 项目：二手交易平台（secondhand2 / com.demo）
- 模块：Day15 履约增强（卖家发货 + 物流追踪 + 发货超时 + 通知联动）
- 文档版本：v1.0（首次冻结｜工程口径对齐版）
- 冻结日期：2026-02-09
- 目标：把 Day15 “做什么 / 不做什么 / 规则口径 / 接口契约 / 数据结构 / 验收标准”一次写死，避免实现过程反复改口径导致返工。

---

## 0. 边界决策（已敲定）
本 Day15 的功能边界以以下开关为准（YES/NO）。**除非升级本文档版本号，否则实现不得越界**。

1. 发货入口：**YES（沿用 `/user/orders/{orderId}/ship`）**
2. 发货状态流转：**YES（仅 `paid -> shipped`）**
3. 发货信息落库：**YES（物流公司 / 运单号 / 发货备注 / ship_time）**
4. 发货时限：**YES（支付后 48 小时内必须发货）**
5. 超时未发货自动处理：**YES（`paid -> cancelled`，`cancel_reason='ship_timeout'`）**
6. 超时退款：**YES（演示环境走 Mock/钱包记账；不接真实退款通道）**
7. 超时处罚：**YES（记录卖家超时违约，触发信用处理）**
8. 物流信息展示：**YES（买家/卖家订单详情与列表展示）**
9. 物流轨迹查询：**YES（提供统一查询接口，支持 mock + 第三方 provider）**
10. 第三方物流 API：**YES（可选接入，不作为 Day15 阻塞项）**
11. 免费演示方案：**YES（默认 `mock provider`，保证零成本可演示）**
12. 通知机制：**YES（站内信必须；短信/Push 仅预留）**
13. 事件驱动联动：**YES（基于 Day14 MQ + Outbox 能力）**
14. 电子面单/打印：**NO（Day15 不做）**
15. 多包裹拆单：**NO（Day15 冻结为一单一运单）**
16. 跨境清关/国际物流：**NO（Day15 不做）**

> 说明：本冻结文档以你工程现有口径为准（例如：`OrderStatus`、`/user/orders`、`order.timeout.pending-minutes=15`、Day14 的 Outbox+MQ 机制）。

---

## 1. 术语与对象定义
- **发货（Ship）**：卖家填写物流公司与运单号后，将订单从 `paid` 推进到 `shipped` 的动作。
- **物流快照**：订单表上的静态物流字段（`shippingCompany/trackingNo/shipTime/shippingRemark`）。
- **物流轨迹（Trace）**：物流节点动态数据（如“已揽件/运输中/派送中/已签收”）。
- **发货超时**：订单支付后超过 48 小时仍未发货的状态。
- **履约超时取消**：系统自动把超时未发货订单由 `paid` 关闭为 `cancelled`，并进入退款流程。
- **物流 Provider**：统一物流查询抽象，支持 `mock` 或第三方实现。
- **退款任务**：超时未发货触发的补偿动作记录（演示环境可落库 + 异步执行）。

---

## 2. 总体架构与生命周期（冻结）
### 2.1 发货主链路
1. 买家支付成功：`pending -> paid`（现有能力）
2. 系统启动“48h 发货时限”计时（MQ 延迟触发为主 + Job 扫描兜底）
3. 卖家发货：写物流信息并更新订单 `paid -> shipped`
4. 买家查看物流：读取订单快照 + 轨迹接口
5. 买家确认收货：`shipped -> completed`（现有能力）

### 2.2 发货超时链路
1. 到达支付后 48h 截止点，订单仍是 `paid`
2. 系统条件更新：`paid -> cancelled`，`cancel_reason='ship_timeout'`
3. 触发退款补偿（mock/wallet 记账）与卖家违约记录
4. 给买卖双方发送通知（站内信）

### 2.3 组件职责
- **OrderService**：发货状态校验、状态流转、幂等与并发安全
- **LogisticsService**：物流轨迹查询、缓存、降级与映射
- **Timeout Processor**：超时未发货判定与自动关单补偿
- **MessageService**：发货/超时/轨迹变更通知
- **MQ + Outbox**：异步触发、重试、可观测

---

## 3. 与现有代码口径对齐（基线行为，必须保持）
### 3.1 现有状态机（已存在）
- 枚举：`pending / paid / shipped / completed / cancelled`
- `shipOrder` 当前口径：仅允许 `paid -> shipped`，且仅卖家可操作
- `confirmOrder` 当前口径：仅允许 `shipped -> completed`，且仅买家可操作

### 3.2 现有发货接口（已存在）
- Controller：`POST /user/orders/{orderId}/ship`
- DTO：`ShipOrderRequest`
  - `shippingCompany`：必填，2~50
  - `trackingNo`：必填，6~50
  - `remark`：可选，<=200

### 3.3 现有超时能力（已存在）
- 已有“待支付超时”规则：`pending` 15 分钟超时关单（Day13/Day14）
- Day15 新增的是“已支付待发货超时（48h）”，不替代现有 15 分钟未支付规则

### 3.4 口径纠偏（冻结）
- 发货后状态不是“`pending -> shipped`”，而是 **`paid -> shipped`**
- 买家看到的“待收货”对应数据库状态 `shipped`

---

## 4. 接口契约冻结（Day15）
### 4.1 卖家发货（沿用）
- `POST /user/orders/{orderId}/ship`
- 请求体：
```json
{
  "shippingCompany": "SF",
  "trackingNo": "SF1234567890",
  "remark": "已检查外观，正常发出"
}
```
- 成功响应：`"发货成功"` 或幂等提示 `"订单已发货，无需重复操作"`

### 4.2 查询物流轨迹（新增）
- `GET /user/orders/{orderId}/logistics`
- 访问权限：订单买家或卖家
- 响应（冻结最小集）：
```json
{
  "orderId": 900001,
  "status": "shipped",
  "shippingCompany": "SF",
  "trackingNo": "SF1234567890",
  "shipTime": "2026-02-09T10:30:00",
  "provider": "mock",
  "lastSyncTime": "2026-02-09T11:00:00",
  "trace": [
    {"time":"2026-02-09T10:35:00","location":"上海","status":"已揽件"},
    {"time":"2026-02-09T11:00:00","location":"上海转运中心","status":"运输中"}
  ]
}
```

### 4.3 卖家待发货列表（沿用 + 约束）
- `GET /user/orders/sell?status=paid`
- 必须支持按“支付时间升序”查看，优先处理即将超时订单

### 4.4 发货提醒接口（新增，可选）
- `POST /internal/orders/ship-reminder/run`（仅内部任务触发）
- 作用：批量扫描临近超时订单（如剩余 24h / 6h / 1h）发送站内提醒

---

## 5. 订单状态机冻结（Day15 版）
允许的状态流转（新增粗体项）：
1. `pending -> paid`（支付）
2. `pending -> cancelled`（买家取消 / 未支付超时）
3. `paid -> shipped`（卖家发货）
4. `shipped -> completed`（买家确认收货）
5. **`paid -> cancelled`（系统判定 48h 未发货超时）**

禁止流转：
- `cancelled`、`completed` 终态不可再流转
- 非卖家不可执行发货
- 非买家不可执行确认收货

---

## 6. 发货规则冻结
### 6.1 卖家发货前置条件
1. 订单存在，且当前用户与订单卖家一致
2. 订单状态必须为 `paid`
3. 物流公司、运单号格式合法（按 DTO 校验）
4. 订单未被系统判定为超时取消

### 6.2 发货写库规则
- 必写字段：`shipping_company / tracking_no / ship_time / status / update_time`
- `status` 固定更新为 `shipped`
- 发货备注写入 `shipping_remark`（可为空）

### 6.3 幂等与并发规则
- 条件更新 where 必须带：`id + seller_id + status=paid`
- 更新行数=1：判定成功
- 更新行数=0：回查最新状态分流
  - 已 `shipped/completed`：返回幂等成功
  - 已 `cancelled`：返回“订单已取消，无法发货”
  - 其他状态：业务异常

### 6.4 物流公司字典冻结（建议）
- 默认字典：`SF / JD / YTO / ZTO / STO / YD / EMS / OTHER`
- Day15 允许字符串直填；Day16 再升级为字典表 + 管理后台维护

---

## 7. 发货超时（48h）与补偿冻结
### 7.1 超时判定口径
- 截止时间 = `pay_time + 48h`
- 到点后若订单仍为 `paid`，触发超时处理

### 7.2 主触发机制（MQ 延迟）
- 支付成功后投递“发货超时消息”（延迟 48h）
- 延迟到期后消费端二次校验订单状态

### 7.3 兜底机制（DB 扫描 Job）
- 周期扫描：`status=paid AND pay_time <= now() - 48h`
- 分批处理，默认 batch=200
- 目的：MQ 故障/消息丢失时保证最终一致

### 7.4 超时处理动作（原子顺序）
1. 条件更新订单：`paid -> cancelled`
2. `cancel_reason='ship_timeout'`
3. 释放商品：`sold -> on_sale`
4. 创建退款任务（演示环境可直接记账成功）
5. 记录卖家违约（`user_violation.violation_type='ship_timeout'`）
6. 通知买卖双方

### 7.5 退款口径（演示环境）
- 退款业务类型：`ORDER_REFUND`
- 强制幂等键：`orderId + refundType(ship_timeout)`
- 不接真实支付网关原路退回；以 mock/wallet 记账为冻结口径

---

## 8. 物流信息同步与展示冻结
### 8.1 订单详情展示
- 必须展示：`shippingCompany / trackingNo / shipTime / shippingRemark`
- 未发货场景字段可为 `null`

### 8.2 轨迹同步策略
- `status=shipped` 后允许拉取轨迹
- 同步频率建议：
  - 发货后 24h 内：每 30 分钟
  - 24h 之后：每 2 小时
  - 已签收/已完成：停止轮询

### 8.3 轨迹状态映射（统一前端口径）
- `PICKED`：已揽件
- `IN_TRANSIT`：运输中
- `OUT_FOR_DELIVERY`：派送中
- `DELIVERED`：已签收
- `EXCEPTION`：异常件

### 8.4 降级策略
- 第三方查询失败时：
  - 不影响订单主流程
  - 返回“暂未获取到最新轨迹，请稍后重试”
  - 保留最近一次成功轨迹 + `lastSyncTime`

---

## 9. 通知机制冻结
### 9.1 必做通道
- 站内信（基于现有 `MessageService`）

### 9.2 可选通道（占位，不阻塞 Day15）
- 短信
- App Push

### 9.3 触发时机
1. 卖家发货成功：通知买家“请关注物流”
2. 物流状态变更（关键节点）：通知买家
3. 临近发货超时：提醒卖家
4. 超时取消 + 退款完成：通知买卖双方

### 9.4 通知模板（冻结最小集）
- 发货成功：`"卖家已发货，请注意查收。订单号：{orderNo}"`
- 超时提醒：`"订单即将超时未发货，请尽快处理。订单号：{orderNo}"`
- 超时取消：`"订单因超时未发货已取消，系统已发起退款。订单号：{orderNo}"`

---

## 10. 事件体系与触发点（Day15 冻结）
### 10.1 事件枚举（在 Day14 基础上扩展）
1. `ORDER_STATUS_CHANGED`（沿用）
2. `ORDER_SHIP_TIMEOUT`（新增）
3. `ORDER_REFUND_CREATED`（新增）
4. `LOGISTICS_TRACE_UPDATED`（可选，建议）

### 10.2 触发点
- 卖家发货成功：发 `ORDER_STATUS_CHANGED(newStatus=shipped)`
- 达到 48h 未发货：发 `ORDER_SHIP_TIMEOUT`
- 退款任务创建：发 `ORDER_REFUND_CREATED`
- 轨迹节点变化：发 `LOGISTICS_TRACE_UPDATED`

### 10.3 幂等规则
- 事件唯一键：`eventId`
- 超时处理幂等键：`orderId + timeoutType(ship_48h)`
- 退款幂等键：`orderId + refundType(ship_timeout)`

---

## 11. 数据结构冻结（Day15 新增/补充）
### 11.1 `orders` 表（沿用）
- 已有字段直接复用：`shipping_company / tracking_no / shipping_remark / ship_time / cancel_reason`
- 新增取消原因值：`ship_timeout`

### 11.2 物流轨迹表（建议新增）
表：`order_logistics_trace`
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL
- `tracking_no` VARCHAR(64) NOT NULL
- `provider` VARCHAR(32) NOT NULL（mock/kdniao/kuaidi100/delivery-tracker）
- `node_time` DATETIME NOT NULL
- `node_location` VARCHAR(128) NULL
- `node_status` VARCHAR(32) NOT NULL
- `raw_payload` TEXT NULL
- `create_time` DATETIME NOT NULL

索引建议：
- `idx_order_time(order_id, node_time desc)`
- `idx_tracking(tracking_no, node_time desc)`

### 11.3 发货超时任务表（建议新增）
表：`order_ship_timeout_task`
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL UNIQUE
- `deadline_time` DATETIME NOT NULL
- `status` VARCHAR(16) NOT NULL（PENDING/DONE/CANCELLED）
- `retry_count` INT NOT NULL DEFAULT 0
- `next_retry_time` DATETIME NULL
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

### 11.4 退款任务表（建议新增）
表：`order_refund_task`
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL UNIQUE
- `refund_type` VARCHAR(32) NOT NULL（ship_timeout/after_sale）
- `amount` DECIMAL(10,2) NOT NULL
- `status` VARCHAR(16) NOT NULL（PENDING/SUCCESS/FAILED）
- `fail_reason` VARCHAR(255) NULL
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

---

## 12. 第三方物流 API 集成（可选）与免费方案冻结
### 12.1 Provider 抽象（必须）
```java
public interface LogisticsProvider {
    LogisticsTrackResult query(String shippingCompany, String trackingNo);
}
```
实现建议：
- `MockLogisticsProvider`（默认）
- `ThirdPartyLogisticsProvider`（可插拔）

### 12.2 零成本演示默认方案（推荐）
- `logistics.provider=mock`
- 通过固定脚本/规则生成轨迹（揽件 -> 运输 -> 派送 -> 签收）
- 优点：零成本、稳定、可重复回归、不依赖外网额度

### 12.3 免费/低成本第三方方案（仅演示可选）
1. `delivery-tracker`（优先推荐）：
   - Cloud Free 计划为 `$0`，且标注“无需信用卡”
   - 同时支持 `Delivery Tracker Core` 自建，官方标注“按许可证免费使用”
2. 快递鸟（KdNiao）：
   - 官方产品页标注“新用户免费领取 7 天试用，限每日前 200 名”
   - 适合短期演示真实轨迹查询
3. 快递100：
   - API 价格页标注“启用 API 后赠送 30 条初始化订单导入余额”
   - 适合短期演示接入链路
4. 17TRACK：
   - 帮助中心标注“每月提供 100 条免费 tracking quota”
   - 超出后需购买套餐

### 12.4 截止 2026-02-09 的落地建议（冻结）
1. **必须**先落地 `mock provider`（零成本、稳定回归）
2. 第三方演示优先 `delivery-tracker`（可 cloud free，也可本地 self-host）
3. 需要中国快递公司演示时，备用 `快递鸟/快递100` 试用额度
4. 任何第三方额度耗尽后，自动回退 `mock provider`

### 12.5 冻结建议（避免 Day15 被第三方阻塞）
- Day15 交付以 `mock provider` 为强制完成项
- 第三方 provider 只要求“可切换 + 通路跑通 1 家”
- 第三方额度耗尽不影响 Day15 验收

---

## 13. 错误码与提示语冻结
- 发货状态非法：`"订单当前状态不允许发货，只能对已付款订单发货"`
- 非卖家发货：`"只有卖家本人可以发货"`
- 发货超时取消：`"订单因超时未发货已自动取消"`
- 物流查询失败：`"暂未获取到最新物流信息，请稍后重试"`
- 退款处理中：`"退款处理中，请稍后查看"`
- 退款失败：`"退款处理失败，请联系客服"`

---

## 14. 监控与可观测性冻结
- 指标：
  - `ship.success.count`
  - `ship.timeout.count`
  - `logistics.query.success/fail`
  - `refund.success/fail`
  - `ship.reminder.sent.count`
- 日志字段统一带：
  - `orderId`、`orderNo`、`sellerId`、`buyerId`、`trackingNo`、`provider`、`traceId`
- 报警建议：
  - 发货超时率 > 5%
  - 物流查询失败率 > 20%
  - 退款失败数 > 0

### 14.1 管理员运维工具（后端阶段冻结新增）
为提升 Day15 联调和回归排障效率，冻结一组管理端运维接口（无需单独管理后台前端，Swagger 可直接调用）。

接口前缀：`/admin/ops/tasks`

1. 任务查询（可观测）
   - `GET /admin/ops/tasks/ship-timeout?orderId=&status=&limit=`
   - `GET /admin/ops/tasks/refund?orderId=&status=&limit=`
2. 手动跑批（不等定时器）
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=`
   - `POST /admin/ops/tasks/refund/run-once?limit=`
3. 人工补偿（失败后人工介入）
   - `POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now`
   - `POST /admin/ops/tasks/refund/{taskId}/reset`

口径说明：
- 发货超时任务无 `FAILED` 状态，失败后仍为 `PENDING`，通过 `trigger-now` 提前到“立即可执行”。
- 退款任务有 `FAILED` 状态，`reset` 用于 `FAILED -> PENDING` 重置重试。
- 所有运维接口都仅用于 Day15 演示与排障，不改变业务主流程冻结口径。

---

## 15. Day15 最小验收标准（全绿定义）
### 15.1 发货能力
1. 卖家可对 `paid` 订单发货成功
2. 发货后订单状态为 `shipped`
3. 物流字段落库完整且前台可见

### 15.2 发货超时
1. `paid` 超过 48h 未发货可被系统自动取消
2. 取消原因固定为 `ship_timeout`
3. 自动触发退款任务与双方通知

### 15.3 物流查询
1. 订单详情可返回物流快照
2. 轨迹接口可返回至少 2 个节点（mock 模式）
3. 第三方不可用时可平稳降级

### 15.4 通知与异步
1. 发货成功通知买家
2. 临近超时提醒卖家
3. 超时取消后通知买卖双方

### 15.5 一致性与幂等
1. 重复发货请求不重复改状态
2. 超时任务重复触发不会重复退款
3. 退款任务支持失败重试

### 15.6 运维可操作性（新增验收）
1. 管理员可查询超时任务与退款任务当前状态
2. 管理员可手动触发单次任务批处理（ship-timeout / refund）
3. 管理员可对异常任务执行人工补偿（trigger-now / reset）

---

## 16. 回归数据约定
- 固定 `paid` 订单 1：用于发货成功流转
- 固定 `paid` 订单 2：`pay_time` 回拨 > 48h，用于超时取消回归
- 固定 `shipped` 订单：用于物流查询与确认收货
- 固定异常运单：用于模拟第三方查询失败降级

---

## 17. 不做什么（Non-goals）
Day15 **不实现**：
- 真实支付网关原路退款（支付宝/微信正式 SDK）
- 电子面单打印、面单订阅回调
- 多包裹拆分发货、部分发货
- 国际物流轨迹与关务能力
- 复杂风控策略（仅保留基础违约记录）

---

## 18. Day15 交付物
- 完成 **订单发货能力**：物流信息填写、状态更新、幂等校验
- 完成 **48h 超时未发货处理**：自动取消、退款任务、违约记录
- 完成 **物流查询展示**：订单详情物流快照 + 轨迹接口（mock 必做）
- 完成 **通知联动**：发货通知、超时提醒、取消退款通知
- 完成 **单元测试 + 集成测试 + 回归脚本**：覆盖核心主链路与异常链路
- 完成 **可观测性**：关键日志、指标、告警阈值
- 完成 **管理员运维工具**：任务查询、手动跑批、人工补偿（后端接口）

---

（文件结束）
