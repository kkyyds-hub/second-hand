# Day18 P6-S3 商品读缓存代码详解 v1.0

- 日期：2026-02-27
- 适用对象：第一次接触这版缓存实现、需要做代码审查的人
- 对应代码：`demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`

---

## 1. 先看结论（3 分钟版）

1. 这次不是“随便加 Redis”，而是有边界的 `cache-aside`：
   - 读：先查缓存，miss 再查 DB，查完回填缓存。
   - 写：不直接改缓存，统一在事务提交后失效缓存（`afterCommit`）。
2. 商品详情和商品列表都做了缓存，但失效方式不同：
   - 详情：精确删 key。
   - 列表：维护一个 `version`，写后把 version +1，老列表 key 自动失效。
3. 并发下避免“所有请求同时回源”：
   - 用短锁（`setIfAbsent + 过期时间`）只让一个线程回源重建。
   - 其他线程短暂等待后再读一次缓存。
4. Redis 异常不会打挂业务：
   - 读取/写入/删键异常都降级回 DB（fail-open）。

---

## 2. 改造前后对比

| 项 | 改造前 | 改造后 |
|---|---|---|
| 商品详情读取 | 每次直查 DB | 优先缓存，miss 回源 DB |
| 商品列表读取 | 每次直查 DB | 优先缓存，miss 回源 DB |
| 写后缓存处理 | 无统一机制 | 统一 `afterCommit` 失效 |
| 列表失效方式 | 无 | `cache:product:list:version` 递增 |
| 击穿防护 | 无 | 重建锁 + 短等待重读 |
| Redis 故障行为 | 无明确策略 | fail-open 回源 DB |

---

## 3. 代码结构总览（按你审查顺序）

建议按这个顺序看代码：

1. 常量和配置项（先看有哪些开关和 TTL）
2. `getMarketProductList`（列表缓存流程）
3. `getMarketProductDetail`（详情缓存流程）
4. `markMarketProductReadCachesDirty` + `runAfterCommitOrNow`（写后失效核心）
5. 下面的工具方法（key 生成、TTL 抖动、锁、safeXXX）

关键行号（当前版本）：

1. 缓存常量：`ProductServiceImpl.java:64`
2. 配置项：`ProductServiceImpl.java:91`
3. 列表缓存入口：`ProductServiceImpl.java:637`
4. 详情缓存入口：`ProductServiceImpl.java:720`
5. 写后失效入口：`ProductServiceImpl.java:1231`
6. 事务后置执行：`ProductServiceImpl.java:1249`
7. TTL 抖动：`ProductServiceImpl.java:1322`
8. 缓存读降级：`ProductServiceImpl.java:1359`
9. 重建锁：`ProductServiceImpl.java:1394`

---

## 4. 常量与配置项，分别代表什么

## 4.1 缓存键常量

1. `MARKET_PRODUCT_DETAIL_KEY_PREFIX`
   - 详情键前缀：`cache:product:detail:`
2. `MARKET_PRODUCT_LIST_KEY_PREFIX`
   - 列表键前缀：`cache:product:list:`
3. `MARKET_PRODUCT_LIST_VERSION_KEY`
   - 列表版本键：`cache:product:list:version`
4. `CACHE_KEY_VERSION_SUFFIX`
   - key 结构版本后缀，当前是 `:v1`
5. `CACHE_NULL_MARKER`
   - 空值占位 `__NULL__`，防穿透

## 4.2 配置项

在 `application.yml` 里对应：

1. `demo.cache.enabled`
   - 全局缓存总开关
2. `demo.cache.product.detail.enabled`
   - 详情缓存开关
3. `demo.cache.product.detail.ttl-seconds`
   - 详情正常缓存 TTL
4. `demo.cache.product.detail.null-ttl-seconds`
   - 详情空值缓存 TTL
5. `demo.cache.product.detail.lock-seconds`
   - 详情重建锁 TTL
6. `demo.cache.product.list.enabled`
   - 列表缓存开关
7. `demo.cache.product.list.ttl-seconds`
   - 列表缓存 TTL
8. `demo.cache.product.list.lock-seconds`
   - 列表重建锁 TTL
9. `demo.cache.product.jitter-percent`
   - TTL 抖动比例（避免同批同时过期）

---

## 5. 列表接口怎么走（逐步解释）

代码入口：`getMarketProductList`（`ProductServiceImpl.java:637`）

执行顺序：

1. 判断缓存开关是否开启。
2. 读列表版本 `cache:product:list:version`。
3. 用 `queryDTO + version` 计算列表 key。
4. 查缓存：
   - 命中：反序列化直接返回。
   - 反序列化失败：删脏缓存，进入重建。
5. 尝试拿重建锁：
   - 拿到锁：查 DB，回填缓存，返回。
   - 没拿到锁：睡 60ms 后重读缓存。
6. 第二次仍没命中：回源 DB 返回。

为什么这么做：

1. 避免高并发 miss 时把 DB 打爆（击穿）。
2. 避免 Redis 序列化脏数据导致请求全失败（脏缓存自愈）。

---

## 6. 详情接口怎么走（逐步解释）

代码入口：`getMarketProductDetail`（`ProductServiceImpl.java:720`）

执行顺序：

1. 参数非空校验。
2. 判断详情缓存开关。
3. 查详情缓存：
   - 命中正常 JSON：反序列化返回。
   - 命中 `__NULL__`：直接抛“商品不可用”。
   - 反序列化失败：删脏缓存，进入重建。
4. 尝试拿重建锁：
   - 拿到锁：查 DB。
     - DB 有数据：回填正常缓存。
     - DB 无数据：回填 `__NULL__`（短 TTL）。
   - 没拿到锁：短等待后重读缓存。
5. 第二次仍无：回源 DB。

空值缓存价值：

1. 对不存在/下架商品，避免每次请求都穿透到 DB。
2. TTL 短，防止状态变化后长时间误判。

---

## 7. 写操作后为什么用 `afterCommit` 失效

核心方法：`markMarketProductReadCachesDirty`（`ProductServiceImpl.java:1231`）

它做两件事：

1. 删除详情缓存 key（精确失效）
2. 递增列表 version（批量失效）

但是它不立即执行，而是调用 `runAfterCommitOrNow`：

1. 有事务：注册 `afterCommit` 回调，提交成功后才删缓存/升版本。
2. 无事务：立即执行。

为什么必须这样：

1. 如果事务还没提交就删缓存，读请求可能查到旧 DB，再把旧值回填进缓存。
2. `afterCommit` 可以显著降低“旧值回填”窗口。

---

## 8. 列表为什么用 version，而不是逐个删列表键

原因：

1. 列表查询维度多（关键字、分类、分页），key 很多，逐个删不可维护。
2. version 方案只需维护一个数字：
   - 写后 `version+1`
   - 新请求自然读新版本 key
   - 旧版本 key 靠 TTL 自然淘汰

代价：

1. Redis 中会短暂存在“旧版本列表 key”。
2. 但不再被新请求访问，属于可接受空间换简化策略。

---

## 9. TTL 抖动、短锁、fail-open 的作用

## 9.1 TTL 抖动（`withTtlJitterSeconds`）

1. 不是所有 key 都同一秒过期，减少雪崩风险。
2. 例：45 秒 TTL，`20%` 抖动后可能在 `36~54` 秒区间。

## 9.2 短锁（`tryAcquireRebuildLock`）

1. key miss 时只允许一个线程去 DB 回源。
2. 其他线程等待后重读，减少 DB 突刺。

## 9.3 fail-open（`safeGetCache/safeSetCache`）

1. Redis 错误只记日志，不抛出中断主流程。
2. 业务优先可用，再由后续请求修复缓存。

---

## 10. 这次改动影响了哪些写链路

凡是会改变市场可见状态/内容的链路，都加了缓存失效调用：

1. 审核通过/驳回
2. 强制下架
3. 管理员手工改状态
4. 卖家编辑商品
5. 卖家下架
6. 删除商品
7. 卖家重新提审 / 上架别名 / 撤回

你可以全局搜 `markMarketProductReadCachesDirty(` 查看触发点。

---

## 11. 你审查时最容易担心的 6 个问题

1. 会不会脏读很久？
   - 详情点删 + 列表 version 失效 + TTL，长期脏读风险低。
2. 会不会锁死？
   - 锁有过期时间，且失败会回源，不会阻塞主流程。
3. Redis 挂了会怎样？
   - 自动回源 DB，接口可用但性能退化。
4. 会不会打爆 Redis 内存？
   - 列表旧版本 key 会残留到 TTL 到期，建议后续观察 key 数量。
5. 反序列化失败怎么办？
   - 直接删该 key，下一次重建。
6. 为什么不用 `@Cacheable`？
   - 当前实现需要精细控制（空值缓存、短锁、version 失效、afterCommit），手写更可控。

---

## 12. 运行态排查手册（你可以直接复制）

## 12.1 看列表 version

```bash
redis-cli GET cache:product:list:version
```

## 12.2 看某商品详情缓存

```bash
redis-cli GET cache:product:detail:{productId}:v1
redis-cli TTL cache:product:detail:{productId}:v1
```

## 12.3 查列表缓存 key（按前缀）

```bash
redis-cli KEYS "cache:product:list:*"
```

## 12.4 验证“写后失效”

1. 先请求一次详情，让缓存命中。
2. 执行编辑/上下架等写操作。
3. 再查详情 key，确认已删除或已重建为新值。
4. 查 `cache:product:list:version` 是否递增。

---

## 13. 你可以这样继续学（建议顺序）

1. 先跑一遍详情流程，盯 `GET/SET/DEL`。
2. 再跑列表流程，理解 version 方案。
3. 最后看 `afterCommit`，理解“为什么不是马上删缓存”。

---

## 14. 相关文档

1. `day18回归/性能治理/Day18_P6_S3_缓存性能规范_v1.0.md`
2. `day18回归/性能治理/Day18_P6_S3_商品读路径缓存落地优化计划_v1.0.md`
3. `day18回归/Day18_Scope_Freeze_v1.0.md`

---

（文件结束）
