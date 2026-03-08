# Day17 Step P2-S2：Review 模块 MP 迁移对照说明

- 文档版本：v1.0
- 日期：2026-02-24
- 迁移类型：通用 CRUD + 简单条件查询从 XML/PageHelper 迁移到 MP。

---

## 1. 改动范围
### 1.1 本次改造模块
1. 主模块：`Review`
2. 对照模块：`Favorite`（已是 MP 形态，本次仅做收口核查，不做破坏性改写）

### 1.2 代码文件
1. `demo-service/src/main/java/com/demo/mapper/ReviewMapper.java`
2. `demo-service/src/main/java/com/demo/service/serviceimpl/ReviewServiceImpl.java`
3. `demo-pojo/src/main/java/com/demo/entity/Review.java`
4. `demo-service/src/main/resources/mapper/ReviewMapper.xml`（已删除）

---

## 2. 对照总览（旧 -> 新）
| 项目 | 迁移前 | 迁移后 | 说明 |
|---|---|---|---|
| Mapper 基类 | 普通 `ReviewMapper` + 自定义方法 | `ReviewMapper extends BaseMapper<Review>` | 统一 MP DAO 基线 |
| Service 基类 | `implements ReviewService` | `extends ServiceImpl<ReviewMapper, Review>` | 减少样板代码 |
| 插入评价 | `reviewMapper.insertReview(review)`（XML） | `this.save(review)`（MP） | 语义等价，仍保留唯一键兜底 |
| 幂等查询 | `selectByOrderIdAndRole`（XML） | `lambdaQuery().eq(...).last(\"LIMIT 1\").one()` | 条件一致 |
| 防刷统计 | `countByBuyerIdSince`（XML） | `lambdaQuery().eq(...).ge(...).count()` | 条件一致 |
| 分页查询 | `PageHelper + listByBuyerId/listByProductId` | `Page<T> + LambdaQueryWrapper` | 同链路只保留 MP 分页 |
| XML 文件 | `ReviewMapper.xml` 存在 | 已删除 | 去除双实现，减少维护面 |

---

## 3. 关键方法对照
### 3.1 创建评价（`createReview`）
1. 保持前置校验不变：订单存在、买家身份、订单状态、幂等检查、防刷限制。
2. SQL 执行路径改为 MP：
   - 旧：`insertReview`
   - 新：`save`
3. 幂等兜底保持不变：仍捕获 `DuplicateKeyException` 并返回业务重复评价错误。

### 3.2 我的评价分页（`listMyReviews`）
1. 旧：`PageHelper.startPage + reviewMapper.listByBuyerId`
2. 新：`this.page(new Page<>(page,pageSize), wrapper)`
3. 查询条件保持一致：
   - `buyer_id = currentUserId`
   - `is_deleted = 0`
   - `order by create_time desc`

### 3.3 商品评价分页（`listProductReviews`）
1. 旧：`PageHelper.startPage + reviewMapper.listByProductId`
2. 新：`this.page(new Page<>(page,pageSize), wrapper)`
3. 查询条件保持一致：
   - `product_id = productId`
   - `is_deleted = 0`
   - `order by create_time desc`

---

## 4. 实体与自动填充对齐
1. `Review` 已继承 `BaseAuditEntity`，统一 `createTime/updateTime` 字段口径。
2. 配合 `AuditMetaObjectHandler`，插入/更新时可走统一服务端时间策略。
3. `isDeleted` 仍保留 `@TableLogic`，逻辑删除语义不变。

---

## 5. 对照模块（Favorite）收口结论
1. `FavoriteMapper` 已是 `BaseMapper`，`FavoriteServiceImpl` 已是 `ServiceImpl`。
2. `restoreDeleted/softDelete` 保留原子 SQL，不强改为 MP 通用方法。
3. 结论：继续作为 MP 标准样板模块，供后续迁移复用。

---

## 6. 保留不改项（边界）
1. `Review` 的跨域依赖（`OrderMapper`、`UserMapper`、`ProductMapper` 读取）本步不迁移。
2. 核心交易域/商品治理域复杂 SQL 维持 XML，不在本步扩边。
3. Controller、DTO、返回结构保持不变。

---

## 7. DoD 对齐说明
1. 迁移接口行为与原接口一致：通过“条件对照 + 分页口径对照 + 幂等兜底”保证。
2. Service/Mapper 重复代码显著下降：
   - 删除 `ReviewMapper.xml`。
   - 删除 `ReviewMapper` 5 个自定义 CRUD/查询方法。
   - 分页与查询逻辑统一收敛到 MP wrapper。

---

（文件结束）
