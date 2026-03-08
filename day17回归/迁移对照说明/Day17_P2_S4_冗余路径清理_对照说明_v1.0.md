# Day17 Step P2-S4：冗余 SQL/重复查询路径清理对照说明

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：在不改变接口契约的前提下，清理重复查询路径与样板代码。

---

## 1. 本次清理范围
### 1.1 主模块（已改造）
1. `ReviewServiceImpl`：清理列表接口中的 N+1 查询路径。
2. `UserMapper/UserMapper.xml`：新增批量查询接口支撑列表聚合。

### 1.2 对照模块（仅核查）
1. `Favorite`：保持现有 MP 样板，不改动 `restoreDeleted/softDelete` 原子 SQL。

---

## 2. 迁移前后对照
| 场景 | 清理前 | 清理后 | 收益 |
|---|---|---|---|
| 我的评价列表 `listMyReviews` | 每条 review 都查 1 次用户 + 1 次商品（N+1） | 批量查用户 + 批量查商品后内存回填 | 查询次数从 `1 + 2N` 降到 `1 + 1 + 1` |
| 商品评价列表 `listProductReviews` | 每条 review 都查 1 次用户 + 1 次同商品（重复） | 批量查用户 + 商品单次查询后回填 | 查询次数从 `1 + 2N` 降到 `1 + 1 + 1` |
| User 批量查能力 | 无 | 新增 `selectByIds` | 支撑列表型聚合降本 |

---

## 3. 代码改动点
1. `demo-service/src/main/java/com/demo/mapper/UserMapper.java`
   - 新增 `selectByIds(@Param(\"userIds\") List<Long> userIds)`。
2. `demo-service/src/main/resources/mapper/UserMapper.xml`
   - 新增 `<select id=\"selectByIds\">`，按 `id in (...)` 批量查询未删除用户。
3. `demo-service/src/main/java/com/demo/service/serviceimpl/ReviewServiceImpl.java`
   - `listMyReviews` 改为批量回填。
   - `listProductReviews` 改为批量回填 + 商品单次查询。
   - 新增 `loadProductsByIds/loadBuyersByIds` 辅助方法。
   - 删除逐条 `userMapper.selectById` 与逐条 `productMapper.getProductById` 路径。

---

## 4. 行为等价说明
1. Controller、DTO、返回结构未改变。
2. 匿名评价展示规则保持不变。
3. 评价内容、评分、时间、商品封面展示逻辑保持不变。
4. 列表排序与分页语义保持不变（仍按 `createTime DESC`）。

---

## 5. 约束与边界
1. 本步只做路径清理，不迁移复杂 SQL 语义。
2. 不改动资金/订单/商品治理核心链路 SQL。
3. 所有复杂 SQL 的“保留理由”继续以 P2-S3 清单为准。

---

## 6. DoD 对齐
1. “无新旧双实现长期共存”：满足（Review 列表路径已统一为批量回填实现）。
2. “迁移后结构更清晰”：满足（查询职责清晰分层，减少重复调用）。
3. “行为无回归”：通过执行复现文档进行接口级验证。

---

（文件结束）
