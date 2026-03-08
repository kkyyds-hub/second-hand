# Day15 实施计划：发货与物流（mock + delivery-tracker）

- 关联冻结文档：`day15回归/Day15_Scope_Freeze_v1.0.md`
- 计划版本：v1.0
- 计划日期：2026-02-09
- 目标：按“`mock` 必做 + `delivery-tracker` 可切换”方案，交付可演示、可回归、可扩展的发货与物流能力。

---

## 0. 实施原则（先定规矩）
1. 先交付 `mock provider` 全链路，再接第三方 provider。
2. 第三方异常不阻塞主交易流程，统一降级到“最近一次轨迹 + 提示语”。
3. 所有状态变更必须使用条件更新，避免并发脏写。
4. 所有异步任务必须有幂等键（超时、退款、轨迹同步）。

---

## 1. 分步实施清单（按依赖顺序执行）

## Step 1：配置与开关骨架（半天）
**目标**：先把 provider 切换能力搭起来，后续开发不返工。

**改动点**
- `application.yml` 增加：
  - `logistics.provider=mock`（默认）
  - `logistics.mock.enabled=true`
  - `logistics.delivery-tracker.base-url`
  - `logistics.delivery-tracker.api-key`
  - `logistics.sync.interval-minutes`
- 新建配置类：`LogisticsProperties`

**验收标准**
1. 启动后日志打印当前 provider（mock/delivery-tracker）。
2. 不配第三方 key 时系统仍可正常启动（mock 可用）。

---

## Step 2：Provider 抽象与数据模型（半天）
**目标**：统一查询接口，防止 Controller/Service 直接耦合第三方。

**改动点**
- 新增接口：`LogisticsProvider#query(shippingCompany, trackingNo)`
- 新增 DTO：
  - `LogisticsTrackResult`
  - `LogisticsTrackNode`
  - `OrderLogisticsVO`
- 新增实现骨架：
  - `MockLogisticsProvider`
  - `DeliveryTrackerProvider`（先空实现 + TODO）
- 新增工厂：`LogisticsProviderFactory`

**验收标准**
1. `mock` 实现能返回标准化轨迹结构。
2. 工厂可按配置返回指定 provider。

---

## Step 3：订单物流查询接口（1 天）
**目标**：先让买家/卖家能看到物流详情和轨迹。

**改动点**
- Controller 新增：`GET /user/orders/{orderId}/logistics`
- Service 新增：`getOrderLogistics(orderId, currentUserId)`
- 权限校验：仅买家/卖家可查
- 状态校验：
  - `pending/paid`：返回快照 + 空轨迹
  - `shipped/completed`：返回快照 + provider 轨迹

**验收标准**
1. 已发货订单可返回轨迹节点。
2. 未发货订单不报错，返回空轨迹。
3. 非订单相关用户访问被拒绝。

---

## Step 4：mock 轨迹规则落地（1 天）
**目标**：零成本演示可立即跑通。

**改动点**
- `MockLogisticsProvider` 规则：
  - `T+0min` 已揽件
  - `T+30min` 运输中
  - `T+90min` 派送中
  - `T+150min` 已签收
- 支持固定 seed（按 `trackingNo` 生成稳定节点时间）
- 新增开关：
  - `logistics.mock.fast-forward=true`（演示时加速）

**验收标准**
1. 同一运单多次查询结果稳定（可重复回归）。
2. 演示模式下 5~10 分钟可看到完整轨迹推进。

---

## Step 5：发货 48h 超时任务模型（1 天）
**目标**：建立“支付后未发货自动取消”的任务基础。

**改动点**
- 新增表（建议）：
  - `order_ship_timeout_task`
  - `order_refund_task`
- 支付成功后创建超时任务（deadline=`pay_time+48h`）
- 幂等键：`order_id` 唯一

**验收标准**
1. 支付成功后任务表出现 `PENDING` 记录。
2. 重复支付回调不产生重复任务。

---

## Step 6：48h 超时执行器（MQ 主链路 + Job 兜底，1.5 天）
**目标**：到点自动关单、退款、通知。

**改动点**
- MQ 延迟消息：
  - 新增 `ORDER_SHIP_TIMEOUT` 事件
  - 新增 delay queue + consumer
- Job 兜底扫描：
  - `status=paid and pay_time<=now-48h`
- 原子更新：
  - `paid -> cancelled`
  - `cancel_reason='ship_timeout'`
- 释放商品：`sold -> on_sale`

**验收标准**
1. 到点后订单自动取消且原因正确。
2. 重复触发不重复取消（条件更新+幂等）。

---

## Step 7：退款补偿（演示版，1 天）
**目标**：超时取消后自动发起退款，形成完整闭环。

**改动点**
- 新增 `RefundService`（演示实现）
- `order_refund_task` 状态机：`PENDING/SUCCESS/FAILED`
- 成功路径：
  - 记账（`wallet_transactions`：`ORDER_REFUND`）
  - 更新退款任务状态

**验收标准**
1. 超时取消会创建退款任务。
2. 退款成功后有流水记录。
3. 失败可重试，且不会重复退款。

---

## Step 8：通知联动（0.5 天）
**目标**：关键节点可见，便于演示。

**改动点**
- 发货成功通知买家
- 临近超时提醒卖家（24h/6h/1h）
- 超时取消 + 退款结果通知买卖双方
- 使用现有 `MessageService`，clientMsgId 做幂等

**验收标准**
1. 三类通知都可在站内消息中看到。
2. 重试/重复事件不会刷屏。

---

## Step 9：delivery-tracker 接入（可选，1 天）
**目标**：在不影响主流程的前提下，跑通一个真实第三方 provider。

**改动点**
- `DeliveryTrackerProvider` 实现 HTTP 调用与响应映射
- 增加超时、重试、熔断与降级
- 失败时回退：
  - 返回最近一次成功轨迹
  - 无缓存则提示“暂未获取到最新物流信息”

**验收标准**
1. 配置 `logistics.provider=delivery-tracker` 后查询通路可用。
2. 三方失败时接口仍返回 200（业务降级，不炸主流程）。

---

## Step 10：测试与回归（1.5 天）
**目标**：保证“发货、超时、退款、轨迹、通知”全链路可回归。

**测试清单**
1. 单元测试：
   - `shipOrder` 状态机/幂等
   - `MockLogisticsProvider` 轨迹生成稳定性
   - 超时执行器幂等
2. 集成测试：
   - 支付 -> 发货 -> 查物流 -> 收货
   - 支付 -> 48h 未发货 -> 自动取消 -> 退款
   - 三方 provider 失败降级
3. 回归脚本：
   - Postman Collection 新增物流与超时场景

**验收标准**
1. 核心链路全绿。
2. 回归脚本可一键重跑。

---

## Step 11：演示脚本与发布前检查（0.5 天）
**目标**：确保你演示时一次成功。

**演示顺序**
1. `mock` 模式：
   - 支付订单 -> 卖家发货 -> 查询轨迹连续推进
2. 超时模式：
   - 构造 `pay_time` 超过 48h -> 自动取消 -> 自动退款通知
3. 三方模式（可选）：
   - 切换 `delivery-tracker` -> 查询真实轨迹 -> 故障降级演示

**发布前检查**
1. 配置默认值与敏感信息脱敏
2. 关键日志可检索（orderId/trackingNo/provider）
3. 指标与告警阈值可观察

---

## 2. 建议排期（可直接执行）
1. D1：Step 1~3（配置骨架 + 抽象 + 查询接口）
2. D2：Step 4~6（mock 轨迹 + 48h 超时执行器）
3. D3：Step 7~8（退款补偿 + 通知）
4. D4：Step 9~11（delivery-tracker 可选 + 测试回归 + 演示脚本）

---

## 3. 风险与应对
1. 风险：第三方 API 不稳定或额度耗尽  
应对：默认 `mock`，第三方失败自动降级
2. 风险：超时任务重复触发导致重复退款  
应对：任务表唯一键 + 条件更新 + 幂等键
3. 风险：并发发货导致状态错乱  
应对：严格 `status=paid` 条件更新 + rows=1 判定成功

---

## 4. 交付清单（计划完成态）
1. 物流查询接口（mock 必做）
2. 48h 未发货自动取消（MQ+Job）
3. 退款补偿闭环（演示版）
4. 通知联动
5. delivery-tracker 可切换接入（可选）
6. 单测/集成测试/回归脚本/演示脚本

---

（文件结束）
