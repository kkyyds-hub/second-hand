# Day12 范围冻结：评价模块（Review）Scope Freeze（最终版）

- 项目：二手交易平台（secondhand2 / com.demo）
- 模块：Day12 评价（买家评价卖家）
- 文档版本：v1.1（最终冻结）
- 冻结日期：2026-01-20
- 目标：把 Day12 “做什么 / 不做什么 / 规则口径 / 接口契约 / 数据结构 / 验收标准”一次写死，避免实现过程反复改口径导致返工。

---

## 0. 边界决策（已敲定）
本 Day12 的功能边界以以下 5 个开关为准（YES/NO）：

1. 匿名评价：**YES**
2. 回评/回复：**NO**
3. 审核/隐藏：**NO**
4. 图文评价：**NO**
5. 联动信用分：**NO**

> 因此：Day12 只交付“买家 -> 卖家”的一次性评价（评分+文字+匿名），并提供必要的查询展示接口与幂等保障。

---

## 1. 术语与对象定义
- **评价（Review）**：对一次已完成交易的反馈记录（评分+文本），用于商品详情/（可选）卖家中心展示。
- **主评价**：买家对卖家的评价（BUYER_TO_SELLER）。Day12 仅此一种角色。
- **匿名**：仅影响展示层用户名/头像的脱敏，不影响后端风控与关联。
- **幂等**：重复提交/并发提交相同评价请求，只能成功一次。

---

## 2. 业务规则冻结（核心口径）
### 2.1 创建评价（买家侧）
**前置条件（必须全部满足）：**
1. 订单必须存在
2. 当前登录用户必须是该订单的 buyer
3. 订单状态必须为 `COMPLETED`
4. 该订单尚未被评价（一次性）

**字段规则：**
- `rating`：整数 1~5
- `content`：最小长度建议 10（防刷评）；最大长度建议 500（防止超长）
- `isAnonymous`：true/false（匿名评价开关 YES，必须实现）

**一次性与幂等（必须实现双保险）：**
- 应用层：创建前先查询是否已存在评价（order_id 维度）
- 数据库层：唯一约束兜底（见 6.2）
- 若并发导致唯一键冲突：统一转换为业务异常（“该订单已评价”）

### 2.2 匿名展示规则（必须冻结，否则前端会打架）
当 `isAnonymous=true`：
- `buyerDisplayName`：返回固定值 **"匿名用户"**
- `buyerAvatar`：返回默认头像 URL 或空字符串（二选一固定，推荐返回空字符串，前端用默认图）
  当 `isAnonymous=false`：
- `buyerDisplayName`：正常返回用户昵称/用户名
- `buyerAvatar`：正常返回头像

> 注意：匿名只影响“展示字段”，数据库仍保存 buyer_id 便于审计与风控（即匿名不等于不可追踪）。

### 2.3 查询与展示
- 商品评价列表：按 `create_time DESC`（默认排序），分页返回
- “我发出的评价”：按 `create_time DESC`，分页返回

---

## 3. 不做什么（Non-goals，明确禁止做）
Day12 **不实现**：
- 回评/回复（卖家回复、买家追评等）
- 审核/隐藏/举报工作流（管理员审核、评价屏蔽）
- 图文评价（图片上传/存储/CDN/清理）
- 评价编辑/撤回（会牵扯一致性与回滚）
- 评价联动信用分（保持 Day12 纯粹收口）
- 复杂反作弊（频控、IP、设备指纹）

---

## 4. 权限与安全冻结
- 所有 `/user/**` 接口：必须登录
- 创建评价：仅订单 buyer 可操作（buyer_id == currentUserId）
- 查询“我发出的评价”：仅本人（buyer_id == currentUserId）
- 商品评价列表：可不登录（若你项目市场页统一需要登录，可改为需登录，但必须在文档与实现同时冻结一致）

---

## 5. 接口契约冻结（建议直接贴进接口文档）
> 返回包装沿用你项目统一 `Result<T>` 与 `com.demo.dto.base.PageResult<T>`  
> 成功：`code = 1`；失败：`code = 0`（按你现有回归断言口径）

### 5.1 创建评价（买家）
- **POST** `/user/reviews`
- Request（JSON）`ReviewCreateRequest`：
```json
{
  "orderId": 900003,
  "rating": 5,
  "content": "物流很快，商品描述一致，沟通顺畅。",
  "isAnonymous": true
}
```
- Response：`Result<Long>`（返回 reviewId）
```json
{ "code": 1, "msg": "success", "data": 12345 }
```

### 5.2 我发出的评价（分页）
- **GET** `/user/reviews/mine?page=1&pageSize=10`
- Response：`Result<PageResult<ReviewItemDTO>>`
```json
{
  "code": 1,
  "msg": "success",
  "data": { "total": 1, "list": [/*...*/], "page": 1, "pageSize": 10 }
}
```

### 5.3 商品评价列表（分页，市场/详情页用）
- **GET** `/user/market/products/{productId}/reviews?page=1&pageSize=10&sortField=createTime&sortOrder=desc`
- Response：`Result<PageResult<ReviewItemDTO>>`

### 5.4 DTO 字段冻结（最小集）
`ReviewItemDTO`（建议字段）：
- `id`
- `orderId`
- `productId`
- `rating`
- `content`
- `isAnonymous`
- `createTime`
- 展示相关（必须实现匿名口径）：
    - `buyerDisplayName`
    - `buyerAvatar`
- （可选）商品展示字段：
    - `productTitle`
    - `productCover`

---

## 6. 数据库结构冻结
### 6.1 表：reviews（表名二选一固定）
推荐表名：`reviews`（简洁、通用）。若你项目已有命名规范，可用 `product_reviews`，但必须“全文一致”。

字段建议（MySQL）：
- `id` BIGINT PK
- `order_id` BIGINT NOT NULL
- `product_id` BIGINT NOT NULL
- `buyer_id` BIGINT NOT NULL
- `seller_id` BIGINT NOT NULL
- `role` TINYINT NOT NULL DEFAULT 1
    - 1 = BUYER_TO_SELLER（Day12 仅使用这个值）
- `rating` TINYINT NOT NULL
- `content` VARCHAR(500) NOT NULL
- `is_anonymous` TINYINT NOT NULL DEFAULT 0
- `is_deleted` TINYINT NOT NULL DEFAULT 0（MP 逻辑删除）
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

### 6.2 唯一约束（强制冻结，保证幂等）
- `UNIQUE KEY uniq_order_role(order_id, role)`

> 说明：用 order 维度锁死“一次交易一次评价”，比 buyer+product 更精确，且更利于并发幂等。

### 6.3 索引建议（冻结为必须）
- `KEY idx_product_time(product_id, create_time)`
- `KEY idx_seller_time(seller_id, create_time)`
- `KEY idx_buyer_time(buyer_id, create_time)`

---

## 7. 错误码与提示语冻结（便于回归断言）
> 你的项目目前多用 BusinessException + msg；Day12 也按这个方式冻结“提示语口径”。

- 订单不存在：`"订单不存在"`
- 无权评价：`"无权评价该订单"`
- 订单未完成不可评价：`"订单未完成，无法评价"`
- 已评价：`"该订单已评价"`
- rating 非法：`"评分必须为1~5"`
- content 过短：`"评价内容不少于10个字"`

---

## 8. Day12 最小验收标准（全绿定义）
1. 买家对 completed 订单创建评价成功
2. 非 completed 订单创建评价失败（提示语命中）
3. 非本人订单创建评价失败
4. 同一订单重复评价失败（并发/重复提交都能稳定失败）
5. 商品评价列表能查到新评价，排序正确（create_time DESC）
6. “我发出的评价”分页返回字段与 `PageResult` 口径一致（total/list/page/pageSize）
7. 匿名评价展示字段口径正确（buyerDisplayName / buyerAvatar）

---

## 9. 回归数据约定（建议固定几条）
- 固定 buyer：`buyer01`
- 固定 seller：`seller01`
- 固定 completed 订单：`orderId = 900003`（你现有回归常用固定 id 的方式）
- 固定 product：与该订单关联的商品（从 order_items 取 product_id）

---

## 10. 未来扩展接口位（仅占位，不实现）
- 回评/回复：`POST /user/reviews/{reviewId}/reply`
- 审核/隐藏：`PUT /admin/reviews/{reviewId}/hide`
- 图文评价：`review_images(review_id, url, sort, is_deleted, create_time...)`
- 信用联动：评价落库后触发信用策略（需额外冻结回滚口径）

---