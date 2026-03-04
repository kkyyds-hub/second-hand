# Day18 P6-S3 商品读路径缓存落地优化计划 v1.0

- 日期：2026-02-26
- 对应步骤：`Step P6-S3：缓存性能与失效策略收口`
- 目标：把“商品列表/详情缓存策略”从文档口径落到代码，实现可开关、可失效、可回滚。

---

## 1. 实施清单（先列待办，完成后回填）

- [x] T1 新增商品读缓存配置与开关（列表/详情分开控制）。
- [x] T2 落地商品详情缓存（cache-aside + TTL + 空值缓存）。
- [x] T3 落地商品列表缓存（cache-aside + TTL + 参数归一化 key）。
- [x] T4 增加热点重建互斥（短锁）防击穿。
- [x] T5 增加写链路 `afterCommit` 失效（详情删键 + 列表版本递增）。
- [x] T6 回填改造前后对比与证据索引。

---

## 2. 设计口径（冻结）

1. 缓存模式：`cache-aside`（读缓存未命中回源 DB 并回填）。
2. 一致性口径：写链路在 `afterCommit` 执行失效动作，避免事务回滚导致缓存提前失效。
3. 失效策略：
   - 详情：删除 `cache:product:detail:{productId}:v1`
   - 列表：递增 `cache:product:list:version`，通过版本切换实现批量失效
4. 失败语义：Redis 异常采用 `fail-open`，回源 DB，不影响主流程。

---

## 3. 改造前 vs 改造后（完成后回填）

| 维度 | 改造前 | 改造后 | 备注 |
|---|---|---|---|
| 商品详情读路径 | DB 直读 | `cache-aside`：先读 `cache:product:detail:{productId}:v1`，miss 回源 DB 并回填 | 不可用商品写入空值标记 `__NULL__`，短 TTL 防穿透 |
| 商品列表读路径 | DB 直读 | `cache-aside`：先读 `cache:product:list:{version}:{queryHash}:v1` | `queryHash` 由 keyword/category/page/pageSize 归一化后生成 |
| 写后失效时机 | 未统一 `afterCommit` | 统一 `afterCommit` 触发：删详情键 + 递增 `cache:product:list:version` | 事务回滚不会误删缓存 |
| 缓存击穿防护 | 无 | 重建时使用短锁（`setIfAbsent + EX`），未拿锁线程短暂等待后重试缓存 | 降低并发 miss 回源放大 |
| 灰度与回滚 | 无细粒度开关 | 新增 `demo.cache.product.detail.enabled` / `demo.cache.product.list.enabled` | 可按缓存点单独关闭，快速回源 DB |

---

## 4. 代码落点（计划）

1. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
2. `demo-service/src/main/resources/application.yml`
3. `demo-service/src/main/resources/application-dev.yml`
4. `day18回归/Day18_Scope_Freeze_v1.0.md`
5. `day18回归/性能治理/README.md`

---

## 5. 实施结果回填

1. 已完成商品详情缓存落地：
   - 读取：缓存命中直接返回；
   - miss：DB 回源并回填；
   - 不可用详情：写空值标记并抛统一业务异常。
2. 已完成商品列表缓存落地：
   - 使用版本键 + 查询签名构造 key；
   - 支持 TTL 抖动，避免同一时刻大量过期。
3. 已完成写链路一致性收口：
   - 在状态迁移、编辑、下架、删除等写链路提交后执行失效动作；
   - 失效逻辑通过 `runAfterCommitOrNow` 统一封装。
4. 已完成配置可控：
   - 支持全局缓存开关与商品列表/详情开关；
   - 支持详情/列表 TTL 与重建锁时长配置。

---

## 6. 证据索引（代码级）

1. `ProductServiceImpl#getMarketProductList`：列表缓存读写与重建逻辑。
2. `ProductServiceImpl#getMarketProductDetail`：详情缓存读写、空值缓存逻辑。
3. `ProductServiceImpl#markMarketProductReadCachesDirty`：写后失效入口。
4. `ProductServiceImpl#runAfterCommitOrNow`：事务后置执行封装。
5. `application.yml` / `application-dev.yml`：`demo.cache.product.*` 配置项。

---

## 7. 验收标准

1. 读路径：列表/详情具备缓存命中与回源重建能力。
2. 一致性：关键写链路提交后可触发缓存失效，避免长期脏缓存。
3. 运维：可通过配置快速关闭商品缓存并回源 DB。

---

（文件结束）
