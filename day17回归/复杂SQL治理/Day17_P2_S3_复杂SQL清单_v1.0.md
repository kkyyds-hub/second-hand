# Day17 Step P2-S3：复杂 SQL 清单（保留并标准化）

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：保留复杂 SQL 在 XML 的实现能力，并补齐可读性与审计信息。

---

## 1. 清单范围
本批次纳入“复杂 SQL”定义：
1. 多表 Join 查询/更新
2. 全文检索（`MATCH ... AGAINST`）
3. 聚合统计（`COUNT/SUM + GROUP BY`）
4. 并发锁语义（`FOR UPDATE`）
5. 状态机原子条件更新

---

## 2. 复杂 SQL 明细（冻结）
| SQL_ID | 文件 | 语句 ID | 类型 | 用途 | 输入 | 索引依赖 | 不迁移原因 |
|---|---|---|---|---|---|---|---|
| P2S3-PROD-001 | `demo-service/src/main/resources/mapper/ProductMapper.xml` | `updateForEditByOwnerAndCurrentStatus` | 条件更新 | 卖家编辑商品并原子回审 | `id, ownerId, currentStatus, targetStatus, title, description, price, images` | `PRIMARY(id)`, `idx(owner_id,status,is_deleted)` | 状态机条件更新依赖 SQL 原子语义，保留 XML 更稳 |
| P2S3-PROD-002 | `demo-service/src/main/resources/mapper/ProductMapper.xml` | `getMarketProductList` | 全文检索 | 市场商品检索与相关性排序 | `keyword, category` | `FULLTEXT(ft_title_desc_ngram)`, `idx(status,is_deleted,create_time)` | FULLTEXT + 排序强依赖执行计划，Wrapper 等价实现可读性差 |
| P2S3-PROD-003 | `demo-service/src/main/resources/mapper/ProductMapper.xml` | `countProductsBySellerId` | 聚合统计 | 卖家商品状态分布 | `sellerId` | `idx(owner_id,status)` | 多 CASE 聚合在 XML 可维护性更高，迁移收益低 |
| P2S3-PROD-004 | `demo-service/src/main/resources/mapper/ProductMapper.xml` | `countProductPublishByDate` | 分组统计 | 指定日期商品发布量分组 | `date` | `idx(create_time,is_deleted)`, `idx(category,create_time)` | `DATE(create_time)+GROUP BY` 仍需 SQL 层优化空间，先保留 |
| P2S3-ORD-001 | `demo-service/src/main/resources/mapper/OrderMapper.xml` | `listBuyerOrders` | 多表查询 | 买家订单分页列表 | `buyerId, status, sortField, sortOrder` | `idx_orders_buyer_status`, `fk_items_order`, `users.PRIMARY` | 多表 Join + 动态排序白名单在 XML 更清晰 |
| P2S3-ORD-002 | `demo-service/src/main/resources/mapper/OrderMapper.xml` | `listSellerOrders` | 多表查询 | 卖家订单分页列表 | `currentUserId, status` | `idx_orders_seller_status`, `fk_items_order`, `products.PRIMARY` | 展示字段跨表且含缩略图处理，保留 XML 风险更低 |
| P2S3-ORD-003 | `demo-service/src/main/resources/mapper/OrderMapper.xml` | `getOrderDetail` | 多表查询 | 订单详情（买卖双方权限） | `orderId, currentUserId` | `orders.PRIMARY`, `fk_items_order`, `users.PRIMARY`, `products.PRIMARY` | 权限条件+多表投影复杂，保留 XML 便于审计 |
| P2S3-ORD-004 | `demo-service/src/main/resources/mapper/OrderMapper.xml` | `releaseProductsForOrder` | Update Join | 取消订单后释放商品状态 | `orderId` | `fk_items_order`, `products.PRIMARY`, `idx(status,is_deleted)` | Update Join 方言特性强，MP 通用层难等价 |
| P2S3-WAL-001 | `demo-service/src/main/resources/mapper/WalletMapper.xml` | `selectByUserIdForUpdate` | 锁查询 | 钱包余额读取并加行锁 | `userId` | `PRIMARY(user_id)` | 资金链路关键锁语义，保留 XML 更明确 |

---

## 3. 结论
1. 本批次保留复杂 SQL 共 9 条，全部已有 `SQL_ID` 与“不迁移原因”。
2. 已在对应 XML 中补齐“用途/输入/索引依赖/不迁移原因”注释块。
3. 后续新增复杂 SQL 必须先登记本清单，再进入开发/评审。

---

（文件结束）
