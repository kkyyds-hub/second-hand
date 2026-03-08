# Day16 总结报告（商品治理：审核 + 下架 + 强制下架 + 举报 + 通知）

- 版本：v1.0
- 日期：2026-02-22
- 项目：二手交易平台（secondhand2 / com.demo）
- 模块范围：Day16 商品治理（Scope Freeze v1.1）
- 目标读者：后端开发 / 测试 / 想复盘并发布技术博客的人

---

## 0. 一句话总结

Day16 的核心价值可以概括为一句话：

> 把“商品状态从 A 到 B 的治理动作”从散落的 if-else，升级为可并发、可幂等、可审计、可通知、可回归的一整套闭环能力。

这套闭环包含：

- **状态机收口**：所有商品状态变更统一走 `ProductServiceImpl` 的“迁移内核”。
- **并发安全**：所有状态更新采用 `id + current_status (+ owner_id)` 条件更新，避免并发误覆盖。
- **幂等口径冻结**：重复下架/重复提审/重复审核/重复处理举报，都能得到可预期响应。
- **审计强一致**：状态变更与审计落库在同一主事务，保证“写状态就必须有审计”。
- **异步通知**：Outbox + MQ + Mongo 落站内信，主链路不被通知失败拖垮。
- **可回归**：提供 Postman/Newman 回归资产与断言口径。

---

## 1. 范围冻结对齐（对齐 `Day16_Scope_Freeze_v1.1.md`）

### 1.1 Day16 明确要做（YES）

- 卖家主动下架：沿用 `/user/products/{productId}/off-shelf`（兼容 `under_review|on_sale -> off_shelf`）
- 管理员审核通过/驳回：沿用 `/admin/products/{productId}/approve|reject`
- 管理员强制下架：新增 `/admin/products/{productId}/force-off-shelf`
- 审核驳回原因落库：复用 `products.reason`
- 商品状态审计日志：新增 `product_status_audit_log`
- 买家举报入口（简版）：新增 `/user/market/products/{productId}/report`
- 管理员处理举报单（简版）：新增 `/admin/products/reports/{ticketNo}/resolve`
- 违规记录与处罚联动：复用 `product_violations`（成立单写违规记录）
- 审核/强制下架/举报处理结果通知：站内信必做（Outbox + MQ + Mongo）
- MongoDB：只用于站内信（消息域存储）
- MQ/Outbox：只用于通知与衍生动作，不承载主状态变更

### 1.2 Day16 明确不做（NO）

- AI 图文审核
- 复杂举报工单（派单/SLA/升级）
- 审核大屏图表
- 高级批量审核
- review_status/product_status 双字段拆分（Day16 维持 `products.status` 单字段）

---

## 2. 状态机（冻结口径）与幂等语义

### 2.1 状态枚举（数据库存储值）

`products.status` 统一使用：

- `under_review`：审核中
- `on_sale`：在售
- `off_shelf`：下架
- `sold`：已售（终态，不回流）

对应枚举：`demo-common/src/main/java/com/demo/enumeration/ProductStatus.java`

### 2.2 允许流转（核心矩阵）

| from | action | to | operator |
|---|---|---|---|
| under_review | approve | on_sale | admin |
| under_review | reject | off_shelf | admin |
| under_review | withdraw | off_shelf | seller(owner) |
| under_review | off_shelf | off_shelf | seller(owner)（兼容） |
| on_sale | off_shelf | off_shelf | seller(owner) |
| under_review / on_sale | force_off_shelf | off_shelf | admin |
| off_shelf | resubmit | under_review | seller(owner) |
| off_shelf | on_shelf | under_review | seller(owner)（提审别名） |
| on_sale / off_shelf / under_review | edit | under_review | seller(owner) |
| on_sale | sold | sold | system(order) |

### 2.3 禁止流转（最关键的一条）

- `sold -> *` 禁止回流（任何动作都不允许把已售商品再改回可售）

### 2.4 幂等语义（冻结口径）

为避免重复请求造成“误报错/误写数据/误写审计”，Day16 明确了幂等返回口径：

- **重复下架**：返回“商品已下架”
- **重复提审（resubmit/on-shelf）且当前已 under_review**：返回当前商品详情（视为成功）
- **重复审核（approve/reject）**：返回“已处理”
- **重复处理举报单**：返回“工单已处理”

---

## 3. 关键实现：统一状态迁移内核（并发安全 + 幂等）

### 3.1 为什么需要“迁移内核”

在没有统一内核时，常见问题是：

- approve/reject/off_shelf/withdraw/resubmit/on_shelf/edit 各写一套逻辑，规则很容易漂移
- 并发时容易出现“后写覆盖前写”，导致状态机行为不可预测
- 幂等语义分散在各方法，回归难、维护难

因此 Day16 抽象了一个统一入口（类似 `transit(...)`），把所有动作都收敛到同一套执行顺序。

实现位置：`demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`

### 3.2 固定执行顺序（所有动作一致）

1. **权限校验**
   - 卖家动作强制 `owner_id == currentUserId`
   - 管理员动作依赖 `/admin/**` 拦截链路（业务层仍保留必要的 operatorId 非空保护）
2. **状态校验**
   - 判断当前状态是否在“允许 from 集合”
   - 某些动作先做“幂等短路”（例如已是 off_shelf 直接返回幂等成功）
3. **条件更新（并发安全核心）**
   - SQL 必须包含 `id + current_status`（卖家动作还必须包含 `owner_id`）
   - rows=1 表示本次确实完成迁移
4. **rows=0 时幂等回查**
   - 回查最新状态
   - 如果命中幂等条件则返回幂等成功
   - 否则按非法状态/更新失败抛业务错误

### 3.3 关键 SQL（避免并发误更新）

管理员动作：`updateStatusAndReasonByCurrentStatus`

- `demo-service/src/main/resources/mapper/ProductMapper.xml`
- 条件：`WHERE id = ? AND is_deleted = 0 AND status = #{currentStatus}`

卖家动作：`updateStatusAndReasonByOwnerAndCurrentStatus`

- 条件：`WHERE id = ? AND owner_id = ? AND is_deleted = 0 AND status = #{currentStatus}`

这两条 SQL 的意义是：

- **并发下不会把“别人已经更新过的状态”再次覆盖**
- 状态机的“from->to”是确定的，不会因为竞态出现“跳转/回流/覆盖”

---

## 4. 审计（主事务强一致）

### 4.1 设计目标

审计日志的目标不是“记录一个字符串”，而是提供可追溯、可对账、可定位问题的证据链：

- 谁（operator_id / role）
- 在什么时候（create_time）
- 对哪个商品（product_id）
- 做了什么动作（action）
- 状态从哪里到哪里（before_status -> after_status）
- 为什么（reason_code/reason_text/extra_json）

### 4.2 为什么要“主事务内写审计”

Day16 采用的策略是：

- 状态变更成功后，**同一事务内**写入 `product_status_audit_log`
- 审计写入失败直接抛业务异常，触发事务回滚

这样可以保证：

- 不会出现“商品状态已经变了，但审计缺失”的追责断链
- 如果审计是合规要求或运营要求，这种策略更可靠

实现：

- 审计服务：`demo-service/src/main/java/com/demo/service/ProductAuditService.java`
- 审计实现：`demo-service/src/main/java/com/demo/service/serviceimpl/ProductAuditServiceImpl.java`
- 表结构：`product_status_audit_log`（见 `day16回归/Day16_DB_Change_List_v1.1.md`）

---

## 5. 举报闭环（简版工单）

### 5.1 为什么单独建表（1A）

Day16 选择 `1A`：新增 `product_report_ticket`，原因：

- 举报是一类“工单”数据：需要 ticketNo、状态、处理人、处理动作、处理备注
- 不适合复用“违规记录”或“用户违规记录”，避免字段语义冲突
- 工单需要幂等处理与并发控制（仅处理 PENDING）

### 5.2 工单状态与动作

状态（DB 约束）：`PENDING / RESOLVED_VALID / RESOLVED_INVALID`

动作（DB 约束）：`dismiss / force_off_shelf`

关键点：表上增加了 CHECK 约束（4B），保证状态值不会被写脏。

### 5.3 处理动作联动

当 `resolve_action = force_off_shelf` 时，必须保证：

- 工单状态更新成功（PENDING -> RESOLVED_VALID）
- 同一请求链路内联动强制下架（走 Step3 的统一迁移与审计）
- 写入 `product_violations`（作为违规记录）

这确保了“工单处理结果”与“商品真实状态”一致，不会出现成立单但商品仍在售。

---

## 6. Outbox + MQ + Mongo 通知链路（异步且可回溯）

### 6.1 目标与边界

目标：

- 审核通过/驳回、强制下架、举报处理结果都要通知相关用户
- 通知失败不影响主链路（状态变更仍成功）

边界：

- 主状态变更只依赖 MySQL 事务
- Mongo 仅存站内信文档（消息域）
- MQ 仅承担“通知事件的异步投递”，不承载主状态

### 6.2 三类事件（Day16）

事件枚举：`demo-pojo/src/main/java/com/demo/dto/mq/ProductEventType.java`

- `PRODUCT_REVIEWED`（审核通过/驳回）
- `PRODUCT_FORCE_OFF_SHELF`（强制下架）
- `PRODUCT_REPORT_RESOLVED`（举报处理结果）

事件 payload DTO：

- `demo-pojo/src/main/java/com/demo/dto/mq/ProductReviewedPayload.java`
- `demo-pojo/src/main/java/com/demo/dto/mq/ProductForceOffShelfPayload.java`
- `demo-pojo/src/main/java/com/demo/dto/mq/ProductReportResolvedPayload.java`

### 6.3 “主事务写 outbox”的意义

发布事件并不是直接发 MQ，而是把事件先落 `message_outbox`：

实现：`demo-service/src/main/java/com/demo/service/serviceimpl/ProductGovernanceEventServiceImpl.java`

好处：

- 业务成功与“事件可追溯”同事务原子性
- MQ 短暂不可用不会影响主链路
- Outbox 表可以作为故障排查与补偿的依据

### 6.4 Outbox 发布任务

发布任务：`demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`

行为：

- 定时扫描 `message_outbox` 的 `NEW/FAIL`
- 成功发送 MQ 后标记 `SENT`
- 失败标记 `FAIL` 并写 nextRetryTime

监控任务：`demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`

### 6.5 消费者幂等与灰度开关

消费者：

- `demo-service/src/main/java/com/demo/mq/consumer/ProductReviewedNoticeConsumer.java`
- `demo-service/src/main/java/com/demo/mq/consumer/ProductForceOffShelfNoticeConsumer.java`
- `demo-service/src/main/java/com/demo/mq/consumer/ProductReportResolvedNoticeConsumer.java`

幂等：

- 先插入 `mq_consume_log`（consumer + eventId 唯一）
- 冲突则 ACK 并退出（代表重复消费）

灰度开关（支持快速隔离）：

- `product.notice.reviewed-enabled`
- `product.notice.force-off-shelf-enabled`
- `product.notice.report-resolved-enabled`

配置位置：`demo-service/src/main/resources/application.yml`

### 6.6 Mongo 站内信落库（系统通知槽位）

系统通知写入：`demo-service/src/main/java/com/demo/service/serviceimpl/SystemNoticeServiceImpl.java`

关键约定：

- `fromUserId = 0`（系统发送人）
- `orderId = 0`（系统通知槽位）
- `clientMsgId` 带固定前缀（幂等键）
  - `SYS-PRODUCT-REVIEWED-<eventId>`
  - `SYS-PRODUCT-FORCE-OFF-SHELF-<eventId>`
  - `SYS-PRODUCT-REPORT-RESOLVED-<eventId>`

### 6.7 P1：系统通知“查询能力”补齐

为什么要补齐：

- Day16 系统通知落 Mongo 时使用 `orderId=0`
- 旧的订单会话消息查询接口强依赖“订单存在 + 买卖家归属”，导致系统通知无法查询

因此新增系统通知专用接口：

- `GET /user/messages/system-notices`（分页列表）
- `GET /user/messages/system-notices/{messageId}`（详情）
- `PUT /user/messages/system-notices/read`（一键已读）

实现：

- `demo-service/src/main/java/com/demo/controller/user/MessageController.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/MessageServiceImpl.java`

查询条件固定为三元组：

- `orderId=0 + fromUserId=0 + toUserId=currentUserId`

这样可以避免把“用户伪造的 orderId=0 消息”混入系统通知，并且权限按收件人隔离。

---

## 7. 数据库对齐结论（基于 2026-02-22 dump）

核对对象：`c:\\Users\\kk\\Desktop\\_localhost__3_-2026_02_22_23_41_19-dump.sql`

结论：

- `products` 已包含 `chk_products_status_day16`
- `product_report_ticket` 已包含 `chk_prt_status_day16` 与 `chk_prt_action_day16`
- `product_status_audit_log` 表结构齐全（包含 `reason_code/reason_text/extra_json`）
- `products.reason` 仅存文本原因（符合 2A，不新增 reason_code 字段）

备注：

- `message_outbox`、`mq_consume_log` 使用 `created_at/updated_at` 命名，这是 Day14 既有表结构；Day16 新增表使用 `create_time/update_time` 命名（符合 3A）。

---

## 8. 接口清单（Day16 涉及）

### 8.1 管理端（admin）

- 待审核列表：`GET /admin/products/pending-approval`
- 审核通过：`PUT /admin/products/{productId}/approve`
- 审核驳回：`PUT /admin/products/{productId}/reject`
- 强制下架：`PUT /admin/products/{productId}/force-off-shelf`
- 处理举报工单：`PUT /admin/products/reports/{ticketNo}/resolve`

### 8.2 用户端（seller/buyer）

卖家商品管理：

- 创建：`POST /user/products`
- 编辑：`PUT /user/products/{productId}`（编辑后统一回审 under_review）
- 下架：`PUT /user/products/{productId}/off-shelf`
- 重提：`PUT /user/products/{productId}/resubmit`
- 上架入口（提审别名）：`PUT /user/products/{productId}/on-shelf`
- 撤回审核：`PUT /user/products/{productId}/withdraw`

买家举报：

- 举报：`POST /user/market/products/{productId}/report`

系统通知：

- 列表：`GET /user/messages/system-notices`
- 详情：`GET /user/messages/system-notices/{messageId}`
- 一键已读：`PUT /user/messages/system-notices/read`

---

## 9. 回归资产（Postman + Newman）

### 9.1 回归文件

本次补齐了 Day16 的回归资产：

- Collection：`day16回归/Day16_Regression.postman_collection.json`
- Environment：`day16回归/Day16_Local_Regression.postman_environment.json`

### 9.2 Newman 一键运行

在项目根目录执行（PowerShell）：

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'; if (Get-Command newman -ErrorAction SilentlyContinue) { newman run "day16回归/Day16_Regression.postman_collection.json" -e "day16回归/Day16_Local_Regression.postman_environment.json" --reporters cli } else { npx --yes newman run "day16回归/Day16_Regression.postman_collection.json" -e "day16回归/Day16_Local_Regression.postman_environment.json" --reporters cli }
```

### 9.3 本次回归踩坑与解决

问题现象：

- 系统通知详情断言失败（`seller/buyer detail has id`）

根因：

- 通知链路是异步的：业务成功 -> outbox 入库 -> 定时任务扫描 -> 发送 MQ -> 消费落 Mongo
- Outbox 发布任务是定时扫描（例如每 5 秒），而 Newman 全套跑完可能 < 5 秒
- 结果是：跑到“通知查询”用例时，Mongo 还没落库，列表为空，详情自然取不到

解决方式：

- Newman 增加请求间隔（示例）：

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'; npx --yes newman run "day16回归/Day16_Regression.postman_collection.json" -e "day16回归/Day16_Local_Regression.postman_environment.json" --delay-request 700 --reporters cli
```

工程性建议（后续可选增强）：

- 把“通知查询”步骤做成可重试断言（轮询 3~5 次，直到列表出现或超时），提升自动化稳定性
- 或者缩短 OutboxPublishJob 扫描周期（需权衡负载与实时性）

---

## 10. 可观测性与排障抓手

建议重点关注的日志关键字（便于 grep / ELK 检索）：

- 商品状态迁移成功/幂等命中（ProductServiceImpl 内 log）
- `Outbox saved` / `Outbox sent success` / `Outbox send failed`
- `OUTBOX_METRICS` / `OUTBOX_ALERT`
- `PRODUCT_REVIEWED` / `PRODUCT_FORCE_OFF_SHELF` / `PRODUCT_REPORT_RESOLVED`
- `duplicate consume`（消费幂等命中）

关键排障表（MySQL）：

- `product_status_audit_log`：核对状态变更是否每次都有审计
- `message_outbox`：看事件是否产生、是否 SENT、失败是否重试
- `mq_consume_log`：看消费者是否已处理、是否 FAIL
- `product_report_ticket`：看工单是否被正确处理、是否幂等

关键排障集合（Mongo）：

- `order_messages`：系统通知与订单消息都在这里（区分 `orderId=0` 与真实 orderId）

---

## 11. 设计权衡与后续优化建议

### 11.1 “审计强一致” vs “业务可用性”

当前策略：审计写入失败 -> 业务回滚。

优点：

- 审计永不缺失，治理动作可追责

代价：

- 审计表异常会影响状态变更可用性

可选替代方案（未来阶段再评估）：

- 审计也走 Outbox 异步落库（牺牲强一致换可用性）
- 或审计落库失败走告警 + 降级（需要明确合规/运营可接受程度）

### 11.2 Outbox 定时扫描带来的“通知延迟”

当前：定时扫描发布，天然存在秒级延迟。

可选优化：

- 发布任务缩短周期，或基于 DB binlog/消息触发（复杂度更高）
- 回归资产在“通知断言”处采用轮询而不是固定时间间隔

### 11.3 查询体验

目前 Day16 系统通知已经补齐“列表/详情/已读”，但仍有可扩展空间：

- 仅统计系统通知未读数（独立于订单会话未读数）
- 支持按类型过滤（reviewed/force-off-shelf/report-resolved）
- 通知内容结构化（当前 content 是纯文本，未来可考虑 payloadJson + 前端模板渲染）

---

## 12. 结论

Day16 已完成从“状态机规则”到“数据一致性、并发安全、幂等语义、审计追溯、异步通知、回归资产”的完整闭环。

如果要把本次总结发布成技术博客，建议主线结构为：

1. 为什么状态机必须收口（避免规则漂移与并发覆盖）
2. 条件更新与幂等回查如何保证并发可预测
3. 为什么审计要主事务强一致，以及取舍
4. Outbox 模式如何让异步通知既可靠又不拖垮主链路
5. 系统通知为什么要从订单会话中拆出来独立查询
6. 回归资产如何保障迭代不回退（以及异步链路的测试技巧）

