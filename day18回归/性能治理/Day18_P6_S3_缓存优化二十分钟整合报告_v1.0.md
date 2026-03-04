# Day18 P6-S3 缓存优化三十分钟整合报告 v1.1

- 日期：2026-03-03
- 适读时长：30 分钟
- 目标：从“知道做了什么”升级到“能讲清为什么这样做、代码怎么跑、面试怎么答”。
- 覆盖范围：P2-S2（缓存一致性基线）+ P6-S3（商品读缓存落地）+ 运行态实测证据。

---

## 1. 先看全局（3 分钟）

Day18 的缓存优化不是单纯“加 Redis”，而是分两步做：

1. 2026-02-25 先做 P2-S2：明确缓存一致性边界，冻结规则（新增读缓存默认 `cache-aside + afterCommit`）。
2. 2026-02-26 再做 P6-S3：把商品列表/详情缓存真正落到代码。
3. 2026-02-27 做动态验证：回填命中、失效、防护证据。

一句话总结：
`cache-aside + afterCommit 失效 + 列表 version + 空值缓存 + 重建锁 + TTL 抖动 + fail-open`。

---

## 2. 术语速记（5 分钟）

1. `cache-aside`
读：先缓存，miss 回源 DB 并回填；写：先写 DB，再删缓存。

2. `afterCommit`
缓存失效动作放在事务提交后执行，避免“事务回滚但缓存已删”的时序问题。

3. `防穿透`
不存在的数据也缓存（如 `__NULL__`），避免恶意/错误请求每次打 DB。

4. `防击穿`
热点 key 过期瞬间，用重建锁让同一时刻只有一个线程回源。

5. `防雪崩`
给 TTL 加随机抖动，避免同一批 key 同时过期。

6. `fail-open`
Redis 异常时不让业务失败，直接回源 DB，优先可用性。

---

## 3. 方案设计总览（4 分钟）

### 3.1 哪些接口缓存，哪些不缓存

1. `GET /user/market/products`（商品列表）：缓存。
2. `GET /user/market/products/{productId}`（商品详情）：缓存。
3. `GET /user/orders/{orderId}`（订单详情）：默认不做长 TTL 缓存（强一致优先）。

### 3.2 缓存键设计

1. 详情：`cache:product:detail:{productId}:v1`
2. 列表：`cache:product:list:{version}:{queryHash}:v1`
3. 列表版本：`cache:product:list:version`

### 3.3 配置参数（当前默认）

1. `demo.cache.enabled=true`
2. `demo.cache.product.detail.ttl-seconds=120`
3. `demo.cache.product.detail.null-ttl-seconds=20`
4. `demo.cache.product.detail.lock-seconds=3`
5. `demo.cache.product.list.ttl-seconds=45`
6. `demo.cache.product.list.lock-seconds=3`
7. `demo.cache.product.jitter-percent=20`

---

## 4. 代码实现拆解（和真实代码对齐，12 分钟）

对应文件：`demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`

### 4.1 商品列表：`getMarketProductList`（约 637 行）

执行路径：

1. 判断列表缓存开关，没开直接查 DB。
2. 读取 `cache:product:list:version`。
3. 对查询参数做归一化（keyword/category/page/pageSize），计算 SHA-256 得到 `queryHash`。
4. 拼列表 key，尝试读缓存。
5. 命中：反序列化返回。
6. 缓存脏数据（反序列化失败）：删 key，进入重建。
7. miss：尝试拿重建锁。
8. 拿到锁：查 DB，写缓存（带 TTL 抖动），返回。
9. 未拿到锁：睡眠 60ms，再读一次缓存。
10. 二次仍无：回源 DB。

设计动机：

1. version 让列表批量失效成本从“遍历删 key”变成 O(1)。
2. query 归一化减少缓存碎片。
3. 锁 + 二次读取降低高并发 miss 的回源放大。

### 4.2 商品详情：`getMarketProductDetail`（约 720 行）

执行路径：

1. 参数校验（`productId` 非空）。
2. 判断详情缓存开关。
3. 读详情 key。
4. 命中正常 JSON：反序列化返回。
5. 命中 `__NULL__`：直接抛“商品不存在/不可查看”。
6. miss：尝试拿重建锁。
7. 拿到锁：查 DB。
8. DB 有数据：回填正常详情缓存（带抖动 TTL）。
9. DB 无数据：回填 `__NULL__`（短 TTL=20s）。
10. 未拿到锁：短等后再读缓存。

设计动机：

1. 空值缓存防穿透。
2. 短锁防击穿。
3. 缓存失败不影响功能返回（后面 safeXXX 兜底）。

### 4.3 写后失效：`markMarketProductReadCachesDirty`（约 1231 行）

关键动作：

1. 删除详情缓存 key（精确失效）。
2. 递增列表 `version`（批量失效）。
3. 包在 `runAfterCommitOrNow` 中，有事务走 `afterCommit`。

为什么必须 `afterCommit`：

1. 若事务未提交就删缓存，其他请求可能读到旧 DB，再把旧值写回缓存。
2. 提交后失效可以显著缩小旧值回填窗口。

### 4.4 关键工具方法（必背）

1. `buildMarketProductListQuerySignature`：参数标准化 + hash，控制 key 数量。
2. `withTtlJitterSeconds`：TTL 抖动，避免同批过期。
3. `tryAcquireRebuildLock`：`SET NX EX` 思路。
4. `safeGetCache` / `safeSetCache` / `safeDeleteCache`：Redis 异常只记日志，走降级。

---

## 5. 并发与一致性时序（3 分钟）

### 5.1 为什么“先写 DB 再删缓存”

1. 先删缓存再写 DB：会有读线程回源到旧 DB，再把旧值回填，产生脏缓存。
2. 先写 DB 再删缓存：读线程即使 miss，回源时读到的是新值。

### 5.2 为什么详情“点删” + 列表“version”

1. 详情 key 可精确定位，点删最直接。
2. 列表组合 key 太多，不适合逐个删；version 递增最稳。

### 5.3 重建锁为什么能防击穿

1. 热点 key 过期时，只有拿到锁的线程查 DB。
2. 其余线程等待并复用重建结果，避免并发打库。

---

## 6. 运行态证据怎么解读（2 分钟）

证据文件：

1. `day18回归/执行记录/Day18_P6_S3_缓存性能执行记录_v1.0.md`
2. `day18回归/执行记录/Day18_P6_S3_动态验证结果_2026-02-27_10-07-49.json`
3. `day18回归/执行记录/Day18_P6_S3_防护演练结果_2026-02-27_10-09-22.json`

关键字段对应关系：

1. `detailExistsAfterFirst=1`：详情缓存命中链路成立。
2. `listVersionBefore/After=2->3`：写后 version 递增成立。
3. `detailExistsAfterWrite=0`：写后详情删键成立。
4. `nullKeyValue=__NULL__` 且 `nullKeyTtl=20`：防穿透链路成立。

---

## 7. 八股高频问答（可背，8 分钟）

1. 问：为什么不用 `@Cacheable`？
答：本项目需要细粒度控制 `afterCommit`、空值缓存、重建锁、version 失效与 fail-open，手写逻辑可控性更高。

2. 问：`cache-aside` 有什么一致性风险？
答：写 DB 与删缓存不是原子操作，异常时可能短时不一致，需要重试、补偿和监控。

3. 问：为什么列表不用逐个删 key？
答：查询组合维度多，遍历删成本高且易漏；version 失效更稳定。

4. 问：空值缓存为什么 TTL 要短？
答：防穿透同时避免“刚创建的数据被长期误判不存在”。

5. 问：重建锁为什么要过期时间？
答：防止线程异常导致永久锁死。

6. 问：TTL 抖动解决什么问题？
答：避免同批 key 同时过期导致雪崩。

7. 问：fail-open 的优缺点？
答：优点是可用性高；缺点是 Redis 异常时 DB 压力会上升。

8. 问：什么场景更适合 fail-closed？
答：安全强约束场景（如风控硬拦截）可能宁可拒绝也不能放行。

9. 问：如何进一步降低脏缓存概率？
答：`afterCommit` + 必要时双删（延迟二次删除）+ 写链路重试补偿。

10. 问：还有哪些防击穿方案？
答：逻辑过期异步刷新、请求合并 singleflight、热点不过期 + 主动失效。

11. 问：穿透、击穿、雪崩的区别？
答：穿透是“查不存在”；击穿是“热点 key 过期瞬间并发回源”；雪崩是“批量 key 同时过期/节点故障”。

12. 问：Redis 高可用里的 Sentinel 和 Cluster 区别？
答：Sentinel 偏故障转移；Cluster 同时做分片+高可用。

13. 问：什么是脑裂？
答：网络分区下可能出现旧主与新主并存写入，导致数据不一致风险。

14. 问：如何减轻脑裂影响？
答：业务真源放 DB、客户端快速切主、合理超时与 quorum、关键写链路幂等与补偿。

15. 问：你这套方案最核心价值是什么？
答：在性能提升同时，把一致性、可用性、可回滚能力一起落地，并且有运行态证据闭环。

---

## 8. 写代码时可直接套用的检查清单

1. 新增缓存点是否定义了 key、TTL、真源、失效触发点？
2. 写路径是否在事务提交后失效缓存？
3. 是否考虑穿透（空值）、击穿（锁）、雪崩（抖动）？
4. Redis 异常是否有降级策略？
5. 是否有开关与回滚路径？
6. 是否有命中率、错误率、key 增长监控？

---

## 9. 30 分钟学习路线（按分钟）

1. 0-5 分钟：看第 1~2 节，掌握核心术语与目标。
2. 5-17 分钟：看第 3~4 节，对照 `ProductServiceImpl` 方法逐段理解。
3. 17-22 分钟：看第 5 节时序，能讲出为什么 `afterCommit`。
4. 22-25 分钟：看第 6 节证据，学会“拿数据证明方案有效”。
5. 25-30 分钟：背第 7 节问答 + 第 8 节清单。

学习完成标准：

1. 你能画出“详情 miss -> 锁 -> 回源 -> 回填”的时序。
2. 你能解释“列表 version 失效”的 trade-off。
3. 你能回答 10 个以上缓存八股且能落回本项目实现。

---

## 10. 关联文档

1. `day18回归/Day18_Scope_Freeze_v1.0.md`
2. `day18回归/缓存一致性治理/Day18_P2_S2_缓存一致性规范_v1.0.md`
3. `day18回归/执行记录/Day18_P2_S2_缓存一致性执行记录_v1.0.md`
4. `day18回归/性能治理/Day18_P6_S3_缓存性能规范_v1.0.md`
5. `day18回归/性能治理/Day18_P6_S3_商品读路径缓存落地优化计划_v1.0.md`
6. `day18回归/性能治理/Day18_P6_S3_商品读缓存代码详解_v1.0.md`
7. `day18回归/执行记录/Day18_P6_S3_缓存性能执行记录_v1.0.md`
8. `day18回归/执行记录/Day18_P6_S3_动态验证结果_2026-02-27_10-07-49.json`
9. `day18回归/执行记录/Day18_P6_S3_防护演练结果_2026-02-27_10-09-22.json`

---

（文件结束）
