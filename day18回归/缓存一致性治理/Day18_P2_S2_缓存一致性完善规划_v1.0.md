# Day18 P2-S2 缓存一致性完善规划 v1.0

- 日期：2026-02-26
- 适用范围：`day18回归/缓存一致性治理`
- 文档定位：在 `Day18_P2_S2_缓存一致性规范_v1.0.md` 基础上，给出可执行、可排期、可验收的补齐方案。
- 读者：后端开发、值班运维、代码审查人。

---

## 0. 先看结论（给审查人的 1 分钟版）

1. 当前项目 Redis 主要是“流程状态键”，不是大规模业务读缓存；一致性总体可控。
2. 但存在 3 个必须补齐的硬缺口：
   - `auth:oauth:*` 缺少 DB 真源；
   - 缓存删除时机未统一到 `afterCommit`；
   - 缓存监控与灰度开关不完整。
3. 执行顺序必须是：
   - 先补真源与一致性底座；
   - 再补监控和开关；
   - 最后试点商品读缓存。
4. 本规划按 5 个里程碑拆解，默认 5~7 个工作日可完成首轮闭环（不含大规模压测）。

---

## 1. Redis 速查（忘记概念时先看这里）

## 1.1 真源（Source of Truth）

1. 真源就是“最终以谁为准”。
2. 业务系统里通常是 DB 真源，Redis 是加速层。
3. 结论：缓存丢了可以重建；真源丢了不可重建。

## 1.2 cache-aside（旁路缓存）

1. 读：先读缓存，未命中再读 DB，再回填缓存。
2. 写：先写 DB，提交后删缓存。
3. 优点：实现简单、最适合现有项目。

## 1.3 afterCommit

1. 含义：只有事务提交成功后，才执行删缓存。
2. 目的：避免“DB 回滚了但缓存已删/已写”，导致短暂脏读或行为错乱。

## 1.4 三个常见风险

1. 缓存穿透：请求不存在的数据，每次都打 DB。
2. 缓存击穿：热点 key 过期瞬间大量并发回源。
3. 缓存雪崩：大量 key 同时过期，DB 突然被打爆。

---

## 2. 当前基线与缺口（基于代码）

## 2.1 当前已具备

1. 关键键模型和 TTL 基本清晰（短信码、频控、激活、登录失败窗口）。
2. 交易主链路（订单/支付）不依赖 Redis 读缓存。
3. `demo.redis.enabled` 已存在，支持全局启停 Redis Bean。

## 2.2 当前缺口

1. 真源缺口：`auth:oauth:{provider}:{externalId}` 仅存 Redis，缺少 DB 映射真源。
2. 一致性缺口：写后删缓存未统一沉淀成 `afterCommit` 工具与约束。
3. 可观测缺口：无统一命中率、键规模、异常膨胀指标。
4. 运维缺口：缺业务级缓存开关（只有全局开关），故障时降级颗粒度不够。

---

## 3. 改造总原则（冻结）

1. DB 仍是业务真源，Redis 只能是缓存或短期状态。
2. 新增业务读缓存一律 `cache-aside`。
3. 涉及事务的删缓存一律 `afterCommit`。
4. 缓存异常默认 `fail-open`（回源 DB），优先保证业务可用。
5. 每个缓存点必须有：key 设计、TTL、失效触发、监控、开关、回滚。

---

## 4. 里程碑计划（详细）

## M1：缓存资产台账与配置分层（P0）

目标：先把“现在到底有哪些键、谁负责、怎么控”固定下来。

任务：
1. 建立缓存资产台账（建议单独文档或附录）：
   - key 前缀
   - 真源
   - TTL
   - 生产/消费代码位置
   - 失效触发
   - 负责人
2. 增加业务级缓存开关配置（先定义，不要求全部启用）：
   - `demo.cache.enabled`
   - `demo.cache.product-detail.enabled`
   - `demo.cache.product-list.enabled`
3. 约定降级语义：缓存不可用时一律回源，不抛 500。

代码落点（预计）：
1. `demo-service/src/main/resources/application.yml`
2. `demo-service/src/main/resources/application-dev.yml`
3. （可选）新增 `demo-common` 配置类 `CacheProperties`

验收：
1. 配置项齐全且有默认值。
2. 文档台账可用于评审，不再靠口头说明。

工时：0.5~1 天。

---

## M2：`auth:oauth` 真源治理（P0，最高优先）

目标：第三方账号映射“可重建、可追溯”。

任务：
1. 新建映射表（示例）：`user_oauth_bind`。
2. 第三方登录流程改为：
   - 先查 DB 映射；
   - 命中后回填 Redis；
   - DB 未命中再创建用户并写 DB 映射；
   - 最后可选写 Redis 缓存。
3. Redis 丢失后可通过 DB 全量恢复。

建议表结构（示例）：
1. `id` bigint PK
2. `provider` varchar(32)
3. `external_id` varchar(128)
4. `user_id` bigint
5. `status` tinyint（1=active）
6. `create_time/update_time`
7. 唯一索引：`uk_provider_external(provider, external_id)`

代码落点（预计）：
1. `AuthServiceImpl`（第三方登录分支）
2. 新增 entity/mapper/service：`UserOauthBind*`
3. 新增 mapper xml 与 DDL 迁移脚本

验收：
1. 清空 Redis 后，第三方登录仍能正确映射到历史账号。
2. 不产生重复用户。

工时：1~1.5 天。

---

## M3：统一 `afterCommit` 删缓存工具（P0）

目标：把“写后删缓存”变成统一能力，避免各处手写。

任务：
1. 新增工具类（示例）：`CacheEvictAfterCommitHelper`。
2. 提供标准方法：
   - `evictAfterCommit(String key)`
   - `evictAfterCommit(Collection<String> keys)`
3. 无事务时立即删；有事务时注册 `TransactionSynchronization` 在 `afterCommit` 删除。
4. 把高风险链路逐步迁移到该工具（先从用户绑定/解绑等开始）。

代码落点（预计）：
1. 新增 `demo-service/src/main/java/com/demo/cache/CacheEvictAfterCommitHelper.java`
2. `UserServiceImpl`
3. `AuthServiceImpl`（可分批）

验收：
1. 事务回滚时不触发删缓存。
2. 事务提交后稳定删除目标 key。

工时：1 天。

---

## M4：监控指标与告警补齐（P1）

目标：缓存问题能被及时发现，而不是靠猜。

任务：
1. 增加最小指标集（可日志打点先行）：
   - `cache_hit_total`
   - `cache_miss_total`
   - `cache_rebuild_total`
   - `cache_error_total`
2. 增加 key 规模巡检：
   - `dau:*` 数量
   - `auth:*` 前缀数量增长趋势
3. 告警建议：
   - 命中率 < 70% 持续 5 分钟
   - 单前缀 key 数量环比异常增长
   - 缓存错误率突增

代码落点（预计）：
1. `day18回归/审计与监控治理/Day18_P4_S2_告警规则清单_v1.0.md`（补缓存指标章节）
2. 相关 service/interceptor 的日志打点

验收：
1. 至少有 1 个可观测面板或固定统计输出。
2. 有明确阈值与处置动作。

工时：1 天。

---

## M5：商品读路径缓存试点（P1）

目标：在低风险路径验证“性能提升 + 一致性不破坏”。

试点范围：
1. 商品详情 `GET /user/market/products/{productId}`
2. 商品列表 `GET /user/market/products`

策略：
1. `cache-aside`
2. TTL：详情 120s，列表 30~60s + 抖动
3. 空值缓存：15~30s
4. 热点互斥重建：`SET NX EX`
5. 写后失效：商品上下架/编辑后 `afterCommit` 删缓存

代码落点（预计）：
1. `ProductServiceImpl`
2. 商品写接口相关 service
3. 统一缓存 key builder（可选新增工具类）

验收：
1. 接口平均耗时下降（压测前后对比）。
2. 写后读无长期脏数据。
3. 缓存不可用时自动回源且业务可用。

工时：1.5~2 天。

---

## 5. 任务拆分清单（可直接建 Jira/禅道）

| 任务ID | 任务 | 优先级 | 预计工时 | 前置依赖 | 输出物 |
|---|---|---|---|---|---|
| C-01 | 缓存资产台账与配置开关定义 | P0 | 0.5d | 无 | 文档+配置项 |
| C-02 | `auth:oauth` DB 真源建模与迁移 | P0 | 1.0d | C-01 | DDL+Mapper+Service |
| C-03 | 第三方登录改造为 DB 真源优先 | P0 | 0.5d | C-02 | 代码+回归记录 |
| C-04 | `afterCommit` 删缓存工具落地 | P0 | 0.5d | C-01 | 公共工具类 |
| C-05 | 用户绑定/解绑链路切换到工具 | P0 | 0.5d | C-04 | 代码改造 |
| C-06 | 缓存指标与告警规则补齐 | P1 | 1.0d | C-01 | 指标+告警文档 |
| C-07 | 商品详情缓存试点 | P1 | 1.0d | C-04,C-06 | 代码+复现记录 |
| C-08 | 商品列表缓存试点 | P1 | 1.0d | C-07 | 代码+复现记录 |

---

## 6. 动态验证清单（每项都要回填）

## 6.1 真源恢复验证

1. 清空 `auth:oauth:*` 键。
2. 用已绑定第三方账号登录。
3. 预期：通过 DB 映射正确命中历史 userId，Redis 被自动回填。

## 6.2 afterCommit 验证

1. 构造“DB 更新后抛异常回滚”场景。
2. 预期：缓存不应被删除。
3. 构造正常提交场景。
4. 预期：提交后缓存被删除。

## 6.3 性能与一致性验证（试点缓存）

1. 接口压测前后对比（QPS、P95）。
2. 写后立即读验证是否出现长期脏值。
3. 缓存服务故障演练，确认自动回源。

---

## 7. 配置建议（示例）

```yaml
demo:
  redis:
    enabled: true
  cache:
    enabled: true
    fail-open: true
    product-detail:
      enabled: false
      ttl-seconds: 120
      null-ttl-seconds: 20
      jitter-percent: 20
    product-list:
      enabled: false
      ttl-seconds: 45
      null-ttl-seconds: 20
      jitter-percent: 20
```

说明：
1. 先定义，默认关闭试点缓存；
2. 通过开关灰度开启；
3. 异常时一键关闭对应缓存点。

---

## 8. 回滚策略（必须提前写）

1. 代码回滚：关闭 `demo.cache.*.enabled`，全部回源 DB。
2. 数据回滚：`user_oauth_bind` 仅新增表，不破坏旧字段，回滚风险低。
3. 运行回滚：发现命中率异常或脏读投诉时，优先关缓存再排查。

---

## 9. 审查人检查清单（你可以直接打勾）

- [ ] 关键键是否都声明了真源与可重建路径。
- [ ] 是否存在“只在 Redis 有数据、DB 无法恢复”的设计。
- [ ] 新增写链路是否全部使用 `afterCommit` 删除缓存。
- [ ] 是否有并发重建防护（空值缓存/互斥锁/TTL 抖动）。
- [ ] 是否有指标、告警、开关、降级、回滚。
- [ ] 执行记录是否包含“成功样例 + 失败演练样例”。

---

## 10. 本规划与现有文档关系

1. 本文是“执行规划”，不替代以下已冻结规范：
   - `day18回归/缓存一致性治理/Day18_P2_S2_缓存一致性规范_v1.0.md`
   - `day18回归/性能治理/Day18_P6_S3_缓存性能规范_v1.0.md`
2. 改造完成后，需同步回填：
   - `Day18_Scope_Freeze_v1.0.md`
   - `day18回归/README.md`
   - 复现步骤与执行记录文档。

---

（文件结束）
