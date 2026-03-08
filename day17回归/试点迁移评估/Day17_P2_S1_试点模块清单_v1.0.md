# Day17 Step P2-S1：试点模块清单（首批迁移对象）

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：用低风险模块验证 MP 迁移方法论，避免全量盲改。

---

## 1. 评估范围与原则
### 1.1 本次评估覆盖模块
1. `Favorite`（收藏）
2. `Review`（评价）
3. `User`（用户基础资料）
4. `Wallet`（钱包）
5. `Product`（商品治理）

### 1.2 试点选择原则
1. SQL 复杂度低（优先单表、少动态 SQL、少聚合）。
2. 回归成本可控（接口少、业务链路短、依赖可控）。
3. 迁移收益明确（能落通用 MP 模板，减少重复代码）。
4. 与高风险资金/状态机链路隔离（不先碰核心交易/治理主链）。

---

## 2. 首批试点模块（冻结）
### 2.1 试点A：`Favorite` 模块（优先级 P0）
#### 迁移范围（本批次纳入）
1. Controller：`/user/favorites` 全部 4 个接口。
2. Service：`FavoriteServiceImpl` 与只读端口 `FavoriteReadPortDbImpl`。
3. Mapper：`FavoriteMapper`（已为 `BaseMapper`，继续工程化收口）。
4. 分页：保持 MP `Page<T>` + 统一 `PageResult<T>`。

#### 保留 SQL 接口（本批次暂不替换）
1. `FavoriteMapper.restoreDeleted(...)`
   - 保留理由：需要单 SQL 原子恢复 `is_deleted=1 -> 0`，并保持幂等语义与并发安全。
2. `FavoriteMapper.softDelete(...)`
   - 保留理由：需要单 SQL 原子软删除 `is_deleted=0 -> 1`，避免先查后改导致并发窗口。
3. `ProductMapper.getProductById(...)`（被收藏流程依赖）
   - 保留理由：属于商品域共享查询，当前挂载 Day16 状态治理链路，不在本试点内改动。
4. `ProductMapper.listByIds(...)`（被收藏列表回填依赖）
   - 保留理由：跨模块读扩展点，先保持现状，避免试点阶段引入商品域回归风险。

#### 迁移收益
1. 作为 MP 标准模板模块，沉淀“单表 CRUD + 幂等更新”可复用方案。
2. 低风险验证自动填充、分页约束、混用守卫规则在真实链路下的稳定性。

---

### 2.2 试点B：`Review` 模块（优先级 P1）
#### 迁移范围（本批次纳入）
1. Controller：
   - `/user/reviews`（创建评价、我的评价）
   - `/user/market/products/{productId}/reviews`（商品评价列表）
2. Service：`ReviewServiceImpl`（评价创建、分页查询、DTO 组装）。
3. Mapper：`ReviewMapper`（作为“XML -> MP 典型迁移”样板候选）。
4. 分页：从 `PageHelper` 迁移为 MP 分页（同链路只保留一种分页机制）。

#### 保留 XML 接口（本批次暂不替换）
1. `OrderMapper.selectOrderBasicById(...)`
   - 保留理由：订单域核心查询，涉及订单状态一致性规则，不在评价域试点内联动改造。
2. `OrderMapper.selectItemsByOrderId(...)`
   - 保留理由：订单明细读取属于交易域基础能力，迁移收益低于跨域回归风险。
3. `UserMapper.selectById(...)`（评价展示昵称/头像）
   - 保留理由：仅只读依赖，当前先保证行为稳定，后续统一在 User 模块改造阶段处理。
4. `ProductMapper.getProductById(...)`（评价展示商品标题/封面）
   - 保留理由：商品域当前处于治理迭代期，试点阶段不扩大改造边界。

#### 迁移收益
1. 验证“历史 XML 模块增量迁移到 MP”完整方法论（Mapper/Service/分页/回归）。
2. 形成第二个可复用样板，避免 MP 实践只停留在 `Favorite` 单模块。

---

## 3. 非试点模块（本阶段明确不纳入）
1. `Product`：高复杂度状态机 + 全文检索 + 多条件动态 SQL，回归成本高。
2. `Wallet`：资金场景含 `FOR UPDATE` 与账务一致性要求，优先稳定性。
3. `User`（全量）：鉴权/绑定/风控/导出混合链路，边界复杂，后置处理。

---

## 4. DoD 对齐检查
1. 每个试点模块都给出“迁移范围说明”：已满足（见 2.1/2.2）。
2. 每个保留 XML 接口都给出“保留理由”：已满足（见 2.1/2.2）。

---

（文件结束）
