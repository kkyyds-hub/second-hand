# Day16 范围冻结：商品下架与商品审核（Scope Freeze）

- 项目：二手交易平台（secondhand2 / com.demo）
- 模块：Day16 商品治理（商品审核 + 主动下架 + 强制下架 + 违规处理）
- 文档版本：v1.1（工程对齐修订版）
- 冻结日期：2026-02-18
- 目标：把 Day16 “做什么 / 不做什么 / 状态流转 / 接口契约 / 数据结构 / 验收标准”一次写死，避免开发过程范围膨胀。

---

## 0. 边界决策（已敲定）
本 Day16 的功能边界以以下开关为准（YES/NO）。**除非升级本文档版本号，否则实现不得越界**。

1. 卖家主动下架：**YES（沿用 `/user/products/{productId}/off-shelf`）**
2. 管理员审核通过/驳回：**YES（沿用 `/admin/products/{productId}/approve|reject`）**
3. 管理员强制下架：**YES（Day16 新增强制下架动作与原因口径）**
4. 审核驳回原因落库：**YES（复用 `products.reason`）**
5. 商品状态审计日志：**YES（新增审计日志表）**
6. 买家举报入口（简版）：**YES（新增基础举报单，不做复杂工单流转）**
7. 违规记录与处罚联动：**YES（复用 `product_violations` + 卖家处罚入口）**
8. 审核结果通知：**YES（站内信必做，MQ+Outbox 异步）**
9. MongoDB 使用：**YES（仅用于站内信文档，不承载核心状态）**
10. MQ/Outbox：**YES（只用于通知与衍生动作，不承载主状态变更）**
11. AI 文本/图片审核：**NO（Day16 不做，后续阶段再接）**
12. 复杂举报工单（派单/SLA/升级）：**NO（Day16 不做）**
13. 审核大屏图表：**NO（Day16 不做）**
14. 高级批量审核：**NO（Day16 不做，保留接口扩展位）**
15. review_status/product_status 双字段拆分：**NO（Day16 维持单字段 `products.status`，后续再拆）**

> 说明：Day16 以当前工程基线为准，`products.status` 使用 `under_review / on_sale / off_shelf / sold`。
> 说明：`PUT /user/products/{productId}/on-shelf` 在当前工程中语义为“重新提审”（`off_shelf -> under_review`），不是直接上架。
> 说明：卖家编辑商品后，状态统一回到 `under_review`（包含原状态为 `on_sale/off_shelf/under_review`）。

---

## 1. 术语与对象定义
- **审核中（under_review）**：商品待管理员审核，不对买家市场展示。
- **在售（on_sale）**：商品可被市场列表检索与下单。
- **下架（off_shelf）**：商品不可购买，但记录保留，可用于历史查询与再提审。
- **已售（sold）**：商品已成交终态，不允许再次编辑/上架。
- **主动下架**：卖家对自己商品执行下架。
- **强制下架**：管理员因违规或风控原因执行下架。
- **举报单**：买家对商品发起违规举报的记录单（简版）。
- **审计日志**：记录商品状态变更前后值、操作人、原因与时间的不可抵赖日志。

---

## 2. 总体架构与生命周期（冻结）
### 2.1 发布-审核-上架主链路
1. 卖家创建商品：状态进入 `under_review`
2. 管理员审核通过：`under_review -> on_sale`
3. 管理员审核驳回：`under_review -> off_shelf`，写驳回原因
4. 卖家修改后重提：`off_shelf -> under_review`

### 2.2 商品下架链路
1. 卖家在售下架：`on_sale -> off_shelf`（`/off-shelf`）
2. 卖家撤回审核：`under_review -> off_shelf`（推荐 `/withdraw`，`/off-shelf` 兼容保留）
3. 管理员强制下架：`under_review|on_sale|off_shelf -> off_shelf`
4. 下架后保留商品与历史订单关联，不影响已成交订单详情查看

### 2.3 违规处理链路（简版）
1. 买家提交举报单（含原因分类与描述）
2. 管理员调查并处理（驳回举报/成立并强制下架）
3. 若成立：写违规记录（`product_violations`）并触发卖家处罚入口

### 2.4 组件职责
- **ProductService**：状态流转、权限校验、条件更新、业务幂等
- **ProductMapper**：状态更新 SQL 与查询筛选
- **ProductViolationMapper**：违规记录写入与查询
- **ProductAuditService（新增）**：状态审计日志落库
- **ReportService（新增）**：举报单创建/处理
- **MQ + Outbox**：审核结果/强制下架通知异步化
- **MessageService（Mongo）**：站内信落库与查询

---

## 3. 与现有代码口径对齐（基线行为，必须保持）
### 3.1 当前商品状态口径（已存在）
- 枚举：`under_review / on_sale / off_shelf / sold`
- 发布：`createProduct` 默认写 `under_review`
- 审核通过：`approveProduct(..., true, null)` -> `on_sale`
- 审核驳回：`approveProduct(..., false, reason)` -> `off_shelf`
- 卖家下架：`offShelfProductStatus` 仅允许 `under_review|on_sale -> off_shelf`

### 3.2 当前接口口径（已存在）
- 用户侧：
  - `GET /user/products`
  - `GET /user/products/{productId}`
  - `POST /user/products`
  - `PUT /user/products/{productId}`
  - `PUT /user/products/{productId}/off-shelf`
  - `PUT /user/products/{productId}/resubmit`
  - `PUT /user/products/{productId}/on-shelf`
  - `PUT /user/products/{productId}/withdraw`
  - `DELETE /user/products/{productId}`
- 管理侧：
  - `GET /admin/products/pending-approval`
  - `PUT /admin/products/{productId}/approve`
  - `PUT /admin/products/{productId}/reject`
  - `GET /admin/products/{productId}/violations`
  - `POST /admin/products/{productId}/violations`

### 3.3 Day16 在基线上的新增点
1. 强制下架动作（统一口径与审计）
2. 举报单简版闭环（创建/处理）
3. 审计日志与异步通知规范化（MQ+Outbox）

### 3.4 v1.1 前置修复项（必须）
1. 修复 `ProductViolationMapper.insert` 参数绑定口径，避免单参数方法与 `#{violation.xxx}` 混用导致运行时绑定失败。
2. Day16 新增接口/表/事件当前尚未在基线实现，验收前必须补齐实现、DDL 与回归脚本。
3. `on-shelf` 接口在 Day16 统一定义为“提审别名”，前后端文案与测试断言不得按“直接上架”执行。

---

## 4. 接口契约冻结（Day16）
### 4.1 卖家主动下架（沿用）
- `PUT /user/products/{productId}/off-shelf`
- 成功响应：`"下架成功"`
- 使用口径：`on_sale` 场景优先使用；`under_review` 撤回建议走 `/withdraw`，但保留兼容。

### 4.2 管理员审核通过（沿用）
- `PUT /admin/products/{productId}/approve`
- 成功响应：`"商品审核通过"`

### 4.3 管理员审核驳回（沿用）
- `PUT /admin/products/{productId}/reject`
- 请求体：
```json
{
  "reason": "商品描述与图片不一致，请补充真实信息"
}
```
- 成功响应：`"商品审核驳回"`

### 4.4 管理员强制下架（Day16 新增）
- `PUT /admin/products/{productId}/force-off-shelf`
- 请求体（冻结最小集）：
```json
{
  "reasonCode": "violation_reported",
  "reasonText": "涉嫌虚假宣传，已先行下架",
  "reportTicketNo": "RPT-20260214-000001"
}
```
- 成功响应：`"强制下架成功"`

### 4.5 买家举报商品（Day16 新增，简版）
- `POST /user/market/products/{productId}/report`
- 请求体（冻结最小集）：
```json
{
  "reportType": "misleading_desc",
  "description": "标题写95新，实物明显破损",
  "evidenceUrls": [
    "https://img.example.com/report/1.png"
  ]
}
```
- 成功响应（返回举报单号）：
```json
{
  "ticketNo": "RPT-20260214-000001"
}
```

### 4.6 管理员处理举报单（Day16 新增，简版）
- `PUT /admin/products/reports/{ticketNo}/resolve`
- 请求体：
```json
{
  "action": "force_off_shelf",
  "remark": "举报成立，已强制下架"
}
```

### 4.7 卖家重新提审（沿用）
- `PUT /user/products/{productId}/resubmit`
- 成功语义：`off_shelf -> under_review`

### 4.8 卖家上架入口（沿用，语义冻结）
- `PUT /user/products/{productId}/on-shelf`
- 冻结语义：等价 `resubmit`，即 `off_shelf -> under_review`，不支持 `off_shelf -> on_sale` 直上架。

---

## 5. 商品状态机冻结（Day16 版）
Day16 采用**单字段状态机**（`products.status`），不做双字段拆分。

### 5.1 允许流转
1. `under_review -> on_sale`（管理员审核通过）
2. `under_review -> off_shelf`（管理员驳回 / 卖家撤回审核 / 管理员强制下架）
3. `on_sale -> off_shelf`（卖家主动下架 / 管理员强制下架）
4. `off_shelf -> under_review`（卖家重提审核，含 `resubmit/on-shelf`）
5. `on_sale|off_shelf|under_review -> under_review`（卖家编辑商品后统一回审）
6. `on_sale -> sold`（下单成交链路）

### 5.2 禁止流转
1. `sold -> under_review|on_sale|off_shelf`（Day16 不支持回流）
2. 非管理员执行审核通过/驳回
3. 非 owner 执行卖家下架/编辑/删除
4. `off_shelf -> on_sale` 直接上架（Day16 统一走“重提审核”）

### 5.3 状态矩阵（冻结）
| 当前状态 | 动作 | 目标状态 | 操作人 | 结果 |
|---|---|---|---|---|
| under_review | approve | on_sale | admin | 允许 |
| under_review | reject | off_shelf | admin | 允许 |
| under_review | withdraw | off_shelf | seller(owner) | 允许 |
| under_review | off_shelf | off_shelf | seller(owner) | 允许（兼容） |
| on_sale | off_shelf | off_shelf | seller(owner) | 允许 |
| on_sale | force_off_shelf | off_shelf | admin | 允许 |
| off_shelf | resubmit | under_review | seller(owner) | 允许 |
| off_shelf | on_shelf | under_review | seller(owner) | 允许（提审别名） |
| on_sale | edit | under_review | seller(owner) | 允许 |
| off_shelf | edit | under_review | seller(owner) | 允许 |
| under_review | edit | under_review | seller(owner) | 允许（幂等） |
| on_sale | sold | sold | system(order) | 允许 |
| sold | any | - | any | 禁止 |

---

## 6. 状态变更规则冻结
### 6.1 权限前置
1. 卖家动作必须 `owner_id == currentUserId`
2. 审核/强制下架必须管理员角色
3. 举报者必须登录买家（或普通用户角色）

### 6.2 条件更新与并发安全
- 所有状态更新都必须条件更新（`id + current_status`）
- 更新行数=1：成功
- 更新行数=0：回查当前状态后做幂等响应

### 6.3 幂等语义
1. 重复通过/驳回同一审核动作：返回“已处理”
2. 重复下架同一商品：返回“商品已下架”
3. 重复处理同一举报单：返回“工单已处理”
4. 重复调用 `resubmit/on-shelf` 且当前已 `under_review`：返回当前商品详情（视为成功）

### 6.4 审计日志强制落库
每次状态变更必须写审计日志，字段最小集：
`product_id / action / operator_id / operator_role / before_status / after_status / reason_code / reason_text / create_time`

---

## 7. 商品审核规则冻结
### 7.1 审核入口
- 管理员从 `GET /admin/products/pending-approval` 拉取待审核商品

### 7.2 审核通过口径
1. 仅 `under_review` 可通过
2. 通过后状态置 `on_sale`
3. 清空 `reason`（避免前端残留旧驳回信息）

### 7.3 审核驳回口径
1. 仅 `under_review` 可驳回
2. 驳回必须提供 `reason`（1~200）
3. 驳回后状态置 `off_shelf`，`reason` 落库

### 7.4 卖家重提口径
1. 仅 `off_shelf` 可重提
2. 重提后状态置 `under_review`
3. 清空历史驳回原因

### 7.5 卖家编辑口径（基线对齐）
1. `sold` 状态禁止编辑
2. `on_sale/off_shelf/under_review` 允许编辑
3. 编辑成功后统一置 `under_review` 并清空 `reason`

---

## 8. 商品下架规则冻结
### 8.1 卖家主动下架
1. 仅 owner 可操作
2. 在售商品使用 `/off-shelf`；审核中撤回优先 `/withdraw`（`/off-shelf` 对 `under_review` 兼容保留）
3. 下架后不可在市场列表展示、不可新下单

### 8.2 管理员强制下架
1. 可从举报处理或后台直接触发
2. 必填 `reasonCode + reasonText`
3. 写审计日志与违规记录（可选）

### 8.3 与订单联动
1. 下架不影响已存在订单的详情展示
2. 新订单创建必须校验商品为 `on_sale`

---

## 9. 举报与违规处理冻结（Day16 简版）
### 9.1 举报单核心字段
`ticket_no / product_id / reporter_id / report_type / description / evidence_urls / status / resolver_id / resolve_action / resolve_remark / create_time / update_time`

### 9.2 状态口径
`PENDING / RESOLVED_VALID / RESOLVED_INVALID`

### 9.3 处理动作口径
1. `dismiss`：举报不成立
2. `force_off_shelf`：举报成立并强制下架

### 9.4 违规记录
- 复用 `product_violations`，记录违规类型、证据、处罚结果

---

## 10. 通知机制冻结（Day16）
### 10.1 必做通知（站内信）
1. 审核通过通知卖家
2. 审核驳回通知卖家（包含驳回原因）
3. 强制下架通知卖家（包含原因）
4. 举报处理结果通知举报人

### 10.2 技术路线
1. 主事务：只做状态变更 + 审计落库 + outbox 入库
2. 异步事务：MQ 消费后写站内信（Mongo）

### 10.3 不做通道
- 短信
- App Push

---

## 11. 事件体系冻结（Day16）
### 11.1 事件定义
1. `PRODUCT_REVIEWED`（通过/驳回）
2. `PRODUCT_FORCE_OFF_SHELF`
3. `PRODUCT_REPORT_RESOLVED`

### 11.2 触发点
1. 审核通过/驳回后发 `PRODUCT_REVIEWED`
2. 强制下架后发 `PRODUCT_FORCE_OFF_SHELF`
3. 举报处理后发 `PRODUCT_REPORT_RESOLVED`

### 11.3 幂等规则
1. 事件唯一键：`eventId`
2. 业务幂等键建议：
   - 审核：`productId + reviewAction + targetStatus`
   - 强制下架：`productId + ticketNo(optional) + reasonCode`
   - 举报处理：`ticketNo + action`

---

## 12. 数据结构冻结（Day16）
### 12.1 复用表
1. `products`：核心商品状态与驳回原因
2. `product_violations`：违规记录

### 12.2 新增表（Day16）
1. `product_status_audit_log`
2. `product_report_ticket`

### 12.3 `product_status_audit_log` 建议字段
- `id` BIGINT PK
- `product_id` BIGINT
- `action` VARCHAR(32)
- `operator_id` BIGINT
- `operator_role` VARCHAR(16)
- `before_status` VARCHAR(32)
- `after_status` VARCHAR(32)
- `reason_code` VARCHAR(64)
- `reason_text` VARCHAR(255)
- `extra_json` JSON
- `create_time` DATETIME

索引建议：
1. `idx_psal_product_time(product_id, create_time desc)`
2. `idx_psal_operator_time(operator_id, create_time desc)`

### 12.4 `product_report_ticket` 建议字段
- `id` BIGINT PK
- `ticket_no` VARCHAR(32) UNIQUE
- `product_id` BIGINT
- `reporter_id` BIGINT
- `report_type` VARCHAR(64)
- `description` VARCHAR(500)
- `evidence_urls` TEXT
- `status` VARCHAR(32)
- `resolver_id` BIGINT NULL
- `resolve_action` VARCHAR(32) NULL
- `resolve_remark` VARCHAR(255) NULL
- `create_time` DATETIME
- `update_time` DATETIME

索引建议：
1. `uk_ticket_no(ticket_no)`
2. `idx_prt_product_status(product_id, status)`
3. `idx_prt_reporter_time(reporter_id, create_time desc)`

---

## 13. 监控与可观测性冻结
### 13.1 日志关键字
1. `product review approve/reject`
2. `product force off-shelf`
3. `product report resolved`
4. `product status audit insert`

### 13.2 关键监控指标
1. 审核通过率（按日）
2. 审核平均耗时（创建到处理）
3. 强制下架次数（按原因分类）
4. 举报成立率

---

## 14. 测试与验收冻结（Day16）
### 14.1 单元测试
1. 审核状态机：`under_review -> on_sale|off_shelf`
2. 卖家下架权限与状态校验
3. 强制下架幂等
4. 举报单重复处理幂等
5. 卖家编辑后统一回审（`on_sale/off_shelf/under_review -> under_review`）
6. `on-shelf` 语义校验（仅提审，不直上架）

### 14.2 集成测试
1. 卖家发布 -> 管理员审核通过 -> 市场可见
2. 卖家发布 -> 管理员驳回 -> 卖家重提
3. 在售商品 -> 卖家下架 -> 市场不可见
4. 买家举报 -> 管理员处理 -> 强制下架 + 通知
5. 在售商品编辑 -> 回到审核中 -> 重新审核通过后再可见
6. `on-shelf` 调用后状态应为 `under_review`（非 `on_sale`）

### 14.3 回归脚本（Newman）
1. `Day16_Local_Regression.postman_environment.json`
2. `Day16_Regression.postman_collection.json`
3. 夹具 SQL：`Day16_Regression_Prepare.sql`

### 14.4 验收标准
1. 所有状态流转均符合第5章矩阵
2. 非法流转全部被拦截并返回业务错误
3. 审计日志完整可追踪
4. 通知异步失败不影响主流程成功

---

## 15. 非目标与后续路线（Day17+）
### 15.1 Day16 明确不做
1. AI 图片/文本审核
2. 工单分配/SLA/催办升级
3. 统计图表大屏
4. 高级批量审核

### 15.2 Day17+ 扩展位
1. review_status 独立字段拆分
2. AI 审核结果作为“建议分”，管理员最终裁决
3. 复杂举报工单系统
4. 可视化审核运营报表

---

## 16. Day16 交付物冻结
1. `Day16_Scope_Freeze_v1.1.md`（本文件，替代 v1.0）
2. 商品审核/下架/强制下架/举报处理接口实现
3. `product_status_audit_log`、`product_report_ticket` 建表脚本
4. MQ+Outbox 通知事件与消费者
5. Day16 回归脚本与复盘文档

---

（文件结束）
