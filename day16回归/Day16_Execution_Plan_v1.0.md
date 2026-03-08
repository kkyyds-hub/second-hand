# Day16 执行计划（Execution Plan）

- 项目：二手交易平台（secondhand2 / com.demo）
- 模块：Day16 商品治理（审核 + 下架 + 强制下架 + 举报 + 通知）
- 计划版本：v1.0（执行草案）
- 计划日期：2026-02-18
- 关联文档：
  - `day16回归/Day16_Scope_Freeze_v1.1.md`
  - `day16回归/Day16_DB_Change_List_v1.1.md`

---

## 0. 目标与边界

### 0.1 目标
1. 按 Day16 冻结文档完整落地商品治理闭环。
2. 保持既有链路稳定（发布、审核、上下架、下单、订单展示）。
3. 新增能力全部具备可观测性、可回归、可审计。

### 0.2 不在本次范围
1. AI 图文审核。
2. 复杂工单（派单/SLA/升级）。
3. 审核大屏与高级批量审核。

---

## 1. 技术方案总览

### 1.1 核心技术
1. 服务框架：Spring Boot + Spring MVC + Spring Transaction。
2. 持久层：MyBatis XML + MySQL。
3. 异步：RabbitMQ + Outbox（`message_outbox`）+ 消费幂等（`mq_consume_log`）。
4. 通知存储：MongoDB（站内信）。
5. 回归：Postman + Newman + SQL 夹具。

### 1.2 关键设计原则
1. 主状态只在 MySQL 内同步事务变更，不依赖 MQ 成功。
2. 所有状态变更强制写 `product_status_audit_log`。
3. 所有对外动作必须经过统一状态机校验和权限校验。
4. 接口幂等优先，重复调用返回业务可接受结果。

---

## 2. Day16 状态流转总表（执行口径）

| 当前状态 | 动作 | 目标状态 | 操作人 | 说明 |
|---|---|---|---|---|
| under_review | approve | on_sale | admin | 审核通过 |
| under_review | reject | off_shelf | admin | 审核驳回，写 reason |
| under_review | withdraw | off_shelf | seller(owner) | 撤回审核 |
| under_review | off-shelf | off_shelf | seller(owner) | 兼容保留 |
| on_sale | off-shelf | off_shelf | seller(owner) | 主动下架 |
| on_sale | force_off_shelf | off_shelf | admin | 强制下架 |
| off_shelf | resubmit | under_review | seller(owner) | 重提审核 |
| off_shelf | on-shelf | under_review | seller(owner) | 提审别名 |
| on_sale/off_shelf/under_review | edit | under_review | seller(owner) | 编辑后统一回审 |
| on_sale | sold | sold | system(order) | 成交链路 |
| sold | any | - | any | 禁止回流 |

---

## 3. 分步执行方案（建议 8 步）

## Step 1：基线冻结与改造入口统一
### 目标
1. 明确 Day16 改造边界，避免“边改边扩”。
2. 把状态相关常量/动作常量收敛到统一位置（枚举或常量类）。

### 技术动作
1. 新增动作枚举（如 `ProductActionType`）：approve/reject/off_shelf/withdraw/resubmit/force_off_shelf/report_resolve。
2. 补充统一错误码或业务错误文案映射，避免前后端口径漂移。
3. 明确 `on-shelf` 与 `resubmit` 的等价语义。

### 注意事项
1. 不改变已有接口 URL 和成功文案（除非冻结文档要求）。
2. 不在本步引入数据库写操作改动。

### 验收
1. 代码中状态/动作硬编码显著减少。
2. 评审通过后再进入状态机改造。

---

## Step 2：状态机与权限收口（Service 层）
### 目标
1. 将所有商品状态变更收口在 ProductService（或独立 StateMachineService）。
2. 所有入口统一走“权限 -> 状态校验 -> 条件更新 -> 幂等返回”。

### 技术动作
1. 抽象状态迁移方法（示例）：`transit(productId, action, operator)`。
2. 条件更新统一使用 `id + current_status (+ owner_id 可选)`。
3. 对重复动作提供幂等结果：
   - 重复下架返回“已下架”
   - 重复提审返回当前详情
   - 重复审核动作返回“已处理”

### 注意事项
1. `sold` 必须全局禁止回流。
2. 编辑商品后的 `under_review` 回审逻辑不得被回归破坏。
3. 任何“直上架”路径（`off_shelf -> on_sale`）必须禁止。

### 验收
1. 状态矩阵每条规则都有单元测试。
2. 旧接口行为与 v1.1 文档一致。

---

## Step 3：管理员强制下架能力
### 目标
1. 落地 `PUT /admin/products/{productId}/force-off-shelf`。
2. 强制下架动作具备审计、可通知、可关联举报单。

### 技术动作
1. 新增请求 DTO：`reasonCode + reasonText + reportTicketNo(optional)`。
2. 执行规则：
   - 允许 `under_review|on_sale|off_shelf -> off_shelf`
   - `products.reason` 写 `reasonText`（2A 方案）
3. 写 `product_status_audit_log`：
   - before/after/action/operator/reason_code/reason_text

### 注意事项
1. `off_shelf -> off_shelf` 也要幂等处理，不要抛 500。
2. 管理员身份依赖 `/admin/**` 拦截链，勿在业务层绕过鉴权前置。

### 验收
1. 强制下架接口通过 happy path + 幂等 path + 非法状态 path。
2. 审计日志完整落库。

---

## Step 4：举报工单闭环（简版）
### 目标
1. 落地买家举报与管理员处理。
2. 实现举报工单状态 `PENDING/RESOLVED_VALID/RESOLVED_INVALID`。

### 技术动作
1. 新增表 `product_report_ticket` 对应实体/Mapper/Service。
2. 新增接口：
   - `POST /user/market/products/{productId}/report`
   - `PUT /admin/products/reports/{ticketNo}/resolve`
3. 处理动作：
   - `dismiss` -> `RESOLVED_INVALID`
   - `force_off_shelf` -> `RESOLVED_VALID` + 触发强制下架流程

### 注意事项
1. 工单处理必须幂等，重复 resolve 返回“已处理”。
2. `force_off_shelf` 不得绕过 Step 3 的统一状态迁移与审计逻辑。
3. `ticket_no` 唯一键冲突需转业务错误而不是 500。

### 验收
1. 举报创建、处理、重复处理都可预期。
2. `force_off_shelf` 场景下工单状态和商品状态一致。

---

## Step 5：审计日志全覆盖
### 目标
1. 所有商品状态变化必须可追踪、可回溯。

### 技术动作
1. 建立统一审计写入入口 `ProductAuditService`。
2. 覆盖动作至少包括：
   - approve/reject/off_shelf/withdraw/resubmit/on_shelf(force alias)/force_off_shelf/edit_resubmit/sold
3. 审计附加信息放 `extra_json`（如 ticketNo、source、clientIp）。

### 注意事项
1. 审计写入失败策略建议“主事务失败回滚”（以审计完整性优先）。
2. 字段命名统一 `create_time`，不要再引入 `created_at` 变体。

### 验收
1. 任意状态变更都能在审计表查到一条对应记录。
2. before/after 与实际商品最终状态一致。

---

## Step 6：事件与通知（Outbox + MQ + Mongo）
### 目标
1. 状态变更后异步通知相关用户。
2. 保证“主流程成功不被通知失败拖垮”。

### 技术动作
1. 新增事件类型：
   - `PRODUCT_REVIEWED`
   - `PRODUCT_FORCE_OFF_SHELF`
   - `PRODUCT_REPORT_RESOLVED`
2. 主事务内写 outbox；由发布任务异步投递 MQ。
3. 消费端幂等：依赖 `mq_consume_log` 或 eventId 去重写 Mongo 站内信。

### 注意事项
1. 不允许直接在主事务里写 Mongo 当作强依赖。
2. 消费失败走重试/死信，不回滚主业务事务。
3. payload 保留最小必要字段，避免泄露隐私。

### 验收
1. 审核通过/驳回、强制下架、举报处理均有站内信。
2. 模拟消费失败后主流程仍成功，重试后消息可补发。

---

## Step 7：回归脚本与自动化验证
### 目标
1. 形成 Day16 可重复执行的回归资产。

### 技术动作
1. 新增：
   - `Day16_Regression.postman_collection.json`
   - `Day16_Local_Regression.postman_environment.json`
   - `Day16_Regression_Prepare.sql`
2. 覆盖链路：
   - 发布 -> 通过
   - 发布 -> 驳回 -> 重提
   - 在售 -> 下架
   - 举报 -> 处理 -> 强制下架 -> 通知
   - 编辑回审与 on-shelf 语义验证

### 注意事项
1. 准备 SQL 要幂等，可重复执行。
2. 断言要区分“业务失败”与“系统异常”。

### 验收
1. Newman 本地可一键跑通。
2. 核心失败用例（非法流转、越权）断言稳定。

---

## Step 8：上线前检查与灰度发布
### 目标
1. 低风险上线，具备快速回滚能力。

### 技术动作
1. 发布前检查：
   - DDL 已执行
   - CHECK 约束生效
   - MQ 配置与消费者存活
2. 灰度策略：
   - 先开管理侧强制下架
   - 再开放举报入口
3. 回滚策略：
   - 应用回滚优先
   - DDL 回滚按 `Day16_DB_Change_List_v1.1.md` 执行

### 注意事项
1. 先验证管理员链路，再验证用户链路。
2. 关注告警：消费积压、outbox 失败率、接口 4xx/5xx 异常波动。

### 验收
1. 关键指标稳定 24h。
2. 无新增 P1/P0 线上事故。

---

## 4. 建议排期（可按 5-7 天执行）
1. D1：Step 1 + Step 2（状态机收口）
2. D2：Step 3（强制下架）
3. D3：Step 4 + Step 5（举报闭环 + 审计）
4. D4：Step 6（事件通知）
5. D5：Step 7（回归脚本）+ Step 8（上线检查）
6. D6-D7：灰度观察与缺陷修复缓冲

---

## 5. 关键风险清单
1. 状态迁移散落多处，导致规则不一致。
2. 幂等缺失导致重复操作写脏数据。
3. 审计漏写导致追责链断裂。
4. 事件重复消费导致重复通知。
5. 举报处理与强制下架解耦不严导致状态不一致。

---

## 6. 完成定义（Definition of Done）
1. v1.1 文档全部“YES 项”有实现与测试证据。
2. 状态矩阵每条规则至少有 1 个自动化测试覆盖。
3. Day16 回归集合在本地与测试环境均可稳定通过。
4. 关键日志与监控指标可观测，告警阈值已配置。
5. 产出 Day16 复盘文档（问题、修复、后续优化项）。
