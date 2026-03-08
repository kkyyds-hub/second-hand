# Day17 Step P2-S1：迁移优先级表（模块分级）

- 文档版本：v1.0
- 日期：2026-02-24
- 说明：本表用于确定“先改谁、后改谁、谁先不改”。

---

## 1. 评分规则
每个模块按 1~5 分评估（分数越高表示越适合先迁移）：

1. 复杂度分（低复杂度高分）
2. 回归成本分（低成本高分）
3. 迁移收益分（高收益高分）
4. 可验证性分（回归可复现程度高分）

综合分（满分 20）用于排优先级。

---

## 2. 模块优先级总表
| 模块 | 参考接口量 | SQL 特征 | 复杂度分 | 回归成本分 | 迁移收益分 | 可验证性分 | 综合分 | 优先级 | 结论 |
|---|---:|---|---:|---:|---:|---:|---:|---|---|
| Favorite | 4 | 单表 + 2 个原子更新 SQL，已是 BaseMapper | 5 | 5 | 4 | 5 | 19 | P0 | 立即作为首批试点 |
| Review | 3（含市场评价列表） | 单表评价 + 跨域只读依赖（订单/用户/商品） | 4 | 4 | 4 | 4 | 16 | P1 | 作为第二试点 |
| User（基础资料） | 4+ | 历史 XML 较多，涉及鉴权/绑定/封禁/导出 | 3 | 3 | 4 | 3 | 13 | P2 | 后置，先做子域拆分再迁移 |
| Wallet | 3 | `FOR UPDATE` + 资金流水 + 提现申请 | 2 | 2 | 3 | 3 | 10 | P2 | 暂不作为首批试点 |
| Product | 8+ | 状态机条件更新 + FULLTEXT `MATCH` + 聚合 | 1 | 1 | 5 | 2 | 9 | P3 | 明确保留 XML，后续专项治理 |

---

## 3. 首批试点排序（冻结）
1. **P0：Favorite**
   - 目标：验证 MP 工程模板（分页、自动填充、幂等更新、文档复现）。
2. **P1：Review**
   - 目标：验证“历史 XML 模块迁移到 MP”的通用迁移路径。

---

## 4. 保留 XML 接口清单（含理由）
> 说明：以下为 P2-S1 阶段“明确不迁移或暂缓迁移”的接口，逐条给出理由。

| 模块 | 接口/方法 | 当前实现 | 保留理由 |
|---|---|---|---|
| Favorite | `FavoriteMapper.restoreDeleted` | 注解 SQL | 原子恢复逻辑 + 并发幂等语义，保留单 SQL 更稳妥 |
| Favorite | `FavoriteMapper.softDelete` | 注解 SQL | 原子软删逻辑，避免“先查后改”并发窗口 |
| Review 依赖 | `OrderMapper.selectOrderBasicById` | XML | 交易域核心读取，跨域改造风险高 |
| Review 依赖 | `OrderMapper.selectItemsByOrderId` | XML | 订单明细读取属于交易域，不在评价试点边界 |
| Review 依赖 | `UserMapper.selectById` | XML | 仅展示信息读取，优先保证稳定 |
| Review 依赖 | `ProductMapper.getProductById` | XML | 商品域处于治理迭代中，当前不扩大改造面 |
| User | `UserMapper.selectUsers` | XML | 动态筛选+分页链路，需与管理端回归一起推进 |
| Wallet | `WalletMapper.selectByUserIdForUpdate` | XML | 资金并发控制关键路径，先稳定不改 |
| Wallet | `WalletMapper.updateBalance` | XML | 账务一致性敏感 SQL，后置专项迁移 |
| Product | `ProductMapper.getMarketProductList` | XML | FULLTEXT 检索与排序优化，迁移风险高 |
| Product | `ProductMapper.updateForEditByOwnerAndCurrentStatus` | XML | 状态机原子条件更新，属于核心治理链路 |
| Product | `ProductMapper.countProductPublishByDate` | XML | 聚合统计 SQL，迁移收益低于回归成本 |

---

## 5. 执行建议（P2-S1 -> P2-S2）
1. 按 `P0 -> P1` 顺序推进，禁止并行跨模块盲改。
2. 每个试点先提交“迁移范围冻结”再动代码。
3. 每完成一个试点，输出“回归结果 + 保留接口说明更新”。

---

（文件结束）
