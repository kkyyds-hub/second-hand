# Day11：收藏模块范围冻结（MyBatis-Plus 落地）

本文件用于冻结 Day11「收藏（Favorites）」模块的**对外接口口径 + 数据库口径 + 关键业务规则 + 工程化约束**，避免后续迭代（Day12 评价、Day13 风控、Day14-16 MQ、未来 Redis）时产生返工或口径漂移。

---

## 1. Day11 交付范围（做了什么）

Day11 在“二手交易平台”中落地了收藏模块，核心交付如下：

1) **数据库新增 `favorites` 表**
- 用于记录用户收藏关系（user_id ↔ product_id）
- 采用逻辑删除（is_deleted），保证“取消收藏”可幂等、可恢复

2) **MyBatis-Plus 在收藏模块落地**
- Favorite 实体：`@TableName("favorites")` + `@TableLogic(value="0", delval="1")`
- FavoriteService：继承 `IService<Favorite>`
- FavoriteServiceImpl：继承 `ServiceImpl<FavoriteMapper, Favorite>`（MP 标准落地方式）
- Mapper：BaseMapper + 两个自定义 SQL（restore / softDelete）

3) **对外接口（用户侧）**
- 收藏：`POST /user/favorites/{productId}`
- 取消收藏：`DELETE /user/favorites/{productId}`
- 我的收藏分页：`GET /user/favorites?page=1&pageSize=10`
- 收藏状态：`GET /user/favorites/{productId}/status`

4) **工程化收口**
- 返回分页统一用 `com.demo.result.PageResult<T>`，并固定 JSON 字段为 `total/list/page/pageSize`
- 收藏读侧预留 Port：`FavoriteReadPort`（未来可替换为 Redis 实现）

---

## 2. 数据库口径冻结（favorites 表）

### 2.1 表结构（字段语义）

表：`favorites`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint auto_increment | 主键 |
| user_id | bigint not null | 收藏人用户 ID |
| product_id | bigint not null | 被收藏商品 ID |
| is_deleted | tinyint(1) default 0 | 逻辑删除：0=正常，1=已取消/删除 |
| create_time | datetime default CURRENT_TIMESTAMP | 创建时间（收藏时间） |
| update_time | datetime on update CURRENT_TIMESTAMP | 更新时间 |

### 2.2 约束与索引（必须保持）

- 唯一键：`UNIQUE(user_id, product_id)`
  - **关键原因**：逻辑删除不会删除行，如果没有“恢复”策略，会导致“取消后再收藏 insert 冲突”
  - Day11 的设计选择是：**不新增(user_id, product_id, is_deleted) 唯一键**，而是固定 `UNIQUE(user_id, product_id)` + “恢复已删除行”策略（见 3.2）

- 常用索引（推荐保持）：
  - `idx_favorites_user_time(user_id, create_time)`：用于“我的收藏”按时间倒序分页
  - `idx_favorites_product(product_id)`：用于统计某商品收藏数

- 外键：
  - `favorites.user_id -> users.id`
  - `favorites.product_id -> products.id`

> ⚠️ 约束变更风险：如果你未来想把唯一键改成 `(user_id, product_id, is_deleted)`，那 Day11 的“恢复策略”就必须同步调整（否则你会产生多行历史记录，读侧口径也要变）。

---

## 3. 业务规则冻结（核心口径）

### 3.1 收藏前置校验（强规则）

- **仅允许收藏在售商品**
  - 商品必须存在，否则报错：`"商品不存在或已被删除"`
  - 商品状态必须为 `on_sale`，否则报错：`"仅在售商品允许收藏"`

> 这条规则用于保证收藏列表展示逻辑、避免收藏已售/下架商品导致前端体验与业务口径混乱。

### 3.2 收藏/取消的幂等与并发语义（强规则）

#### 收藏（POST /user/favorites/{productId}）
冻结口径：**先恢复、再插入；并发 DuplicateKey 当成功**。

流程：
1) `restoreDeleted(userId, productId)`：只命中 `is_deleted=1` 的记录，将其恢复为 `is_deleted=0`，并重置 `create_time/update_time`
2) 若恢复成功（影响行数 > 0），视为收藏成功
3) 若未恢复（说明不存在删除记录），执行 insert
4) 若并发导致 `DuplicateKeyException`，视为幂等成功（返回收藏成功）

#### 取消收藏（DELETE /user/favorites/{productId}）
冻结口径：**软删除幂等**。

- 执行 `softDelete(userId, productId)`：只对 `is_deleted=0` 的记录更新为 1
- 如果本来没收藏/已取消，更新行数为 0，但接口仍返回成功（favorited=false）

---

## 4. 接口契约冻结（路径、入参、返回）

> 所有 `/user/**` 接口：必须带登录鉴权头（项目当前使用 header `authentication: <token>`）。

### 4.1 收藏接口

**POST** `/user/favorites/{productId}`

- Path 参数：
  - `productId`：`@Min(1)`

- 返回：`Result<FavoriteActionResponse>`
  - `FavoriteActionResponse`：
    - `productId: Long`
    - `favorited: Boolean`（true）

### 4.2 取消收藏接口

**DELETE** `/user/favorites/{productId}`

- Path 参数：
  - `productId`：`@Min(1)`

- 返回：`Result<FavoriteActionResponse>`
  - `favorited`：false（幂等语义：未收藏也返回 false 且 code=1）

### 4.3 我的收藏分页

**GET** `/user/favorites`

- Query 参数（PageQueryDTO）：
  - `page` 默认 1
  - `pageSize`（优先）或 `size`（兼容字段）
  - 其他字段（status/sortField/sortOrder）在收藏模块**不作为过滤口径**（留作统一 DTO 兼容）

- 返回：`Result<PageResult<FavoriteItemDTO>>`

`PageResult<T>` 对外 JSON 字段冻结：
- `total`
- `list`
- `page`
- `pageSize`

`FavoriteItemDTO` 字段冻结：
- `productId`
- `favoritedAt`（收藏时间，来自 favorites.create_time）
- `price`（BigDecimal）
- `title`
- `coverUrl`（从 products.images 取第 1 张，逗号分隔）
- `status`（商品当前状态）

列表排序冻结：
- 以 `favorites.create_time desc` 排序（越新收藏越靠前）

### 4.4 收藏状态接口

**GET** `/user/favorites/{productId}/status`

- 返回：`Result<Boolean>`
  - true：存在 `favorites(user_id, product_id, is_deleted=0)`
  - false：不存在或已取消（is_deleted=1）

---

## 5. MyBatis-Plus 落地规范冻结

Day11 对 MP 的使用范围**只限定在收藏模块**，其他模块不强制改造。

### 5.1 必须项

- 配置分页拦截器：`MybatisPlusInterceptor + PaginationInnerInterceptor(DbType.MYSQL)`
- Entity 必须显式声明逻辑删除值：
  - `@TableLogic(value="0", delval="1")`
  - 避免不同默认策略导致线上/本地口径不一致

### 5.2 允许的自定义 SQL（Day11 选择）

Day11 明确允许在 MP 场景下写少量自定义 SQL，原因：
- “恢复逻辑删除记录”是 **unique(user_id, product_id)** 场景下的必需能力
- “软删除幂等更新”也适合用单条 update 精准表达

因此 FavoriteMapper 保留：
- `restoreDeleted(userId, productId)`
- `softDelete(userId, productId)`

### 5.3 禁止项（防返工）

- 收藏模块不要混用 PageHelper（MP 分页与 PageHelper 会产生口径/线程变量冲突）
- 不要在 ServiceImpl 里读取 BaseContext（当前实现由 Controller 取 userId 并入参传入，保持可测/可复用）

---

## 6. Redis 预留接口位（冻结设计，不落实现）

Day11 预留“未来 Redis 接入点”采用 Port 模式：

接口：`FavoriteReadPort`
- `boolean isFavorited(Long userId, Long productId)`
- `long countByProductId(Long productId)`

当前实现：`FavoriteReadPortDbImpl`（直接查 DB）
未来可替换为：`FavoriteReadPortRedisImpl`（读缓存 + 回源）

> 目的：后续在商品详情页/列表页需要展示“收藏状态/收藏数”时，不把 Redis 逻辑侵入 FavoriteServiceImpl 的核心写链路。

---

## 7. Day11 回归范围冻结（Newman）

Day11 最小回归必须覆盖：

1) Buyer 登录拿 token
2) 预清理：取消收藏（幂等）
3) 收藏成功
4) 我的收藏列表包含该商品（分页字段 total/list/page/pageSize 正确）
5) 重复收藏幂等（不会报错）
6) 取消收藏成功
7) 重复取消幂等（不会报错）
8) 收藏列表不再包含该商品
9) （可选）收藏状态接口：收藏前 false / 收藏后 true / 取消后 false

回归数据建议（便于稳定复跑）：
- 固定一个 `on_sale` 商品作为回归目标（例如 productId=48），否则会触发“仅在售商品允许收藏”的校验失败。

---

## 8. 常见踩坑清单（Day11 已规避/需保持）

1) **逻辑删除 + 唯一键冲突**
- UNIQUE(user_id, product_id) + 逻辑删除 => “取消后再收藏”不能 insert
- Day11 采用“恢复策略”解决：restore -> insert

2) **收藏只允许 on_sale**
- 如果你回归使用的商品不是 `on_sale`，收藏接口会直接失败
- 所以回归一定要准备 `on_sale` 商品（或用补丁脚本修复）

3) **分页字段口径不一致**
- PageResult 的 JSON 字段固定为 `list/total/page/pageSize`，不要暴露 `records/size/pages`

4) **价格类型**
- FavoriteItemDTO.price 建议与 Product.price 一致（当前为 BigDecimal），避免序列化/精度问题

---

## 9. Day12/Day13 扩展建议（不属于 Day11，但提前冻结边界）

- Day12 评价：不要让评价逻辑侵入收藏写链路；收藏列表展示“评价信息”应走聚合读模型/额外接口
- Day13 风控封禁：如果用户被封禁，收藏接口是否拦截应由统一鉴权/拦截器处理（不要在 FavoriteServiceImpl 里写封禁判断）
- 收藏数：建议由 `FavoriteReadPort.countByProductId` 承接，未来接 Redis 再做一致性策略（写 DB 后异步刷新缓存）

---

（文件结束）
