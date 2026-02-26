# Day18 P6-S3 缓存性能规范 v1.0

- 日期：2026-02-26
- 对应阶段：`Step P6-S3：缓存性能与失效策略收口`
- 目标：在不破坏一致性的前提下，让关键读路径具备可落地的缓存加速策略，且失效行为可解释、可复现。

---

## 1. 现状基线（代码事实）

1. 当前 Redis 主要用于“短期状态与风控计数”，不是业务主读缓存：
   - 验证码/频控：`auth:sms:code:*`、`auth:sms:rate:*`
   - 激活令牌：`auth:email:activation:*`
   - 登录失败窗口：`auth:login_fail:*`
   - 指标集合：`dau:{yyyy-MM-dd}`
2. 当前关键读链路（商品列表/详情、订单详情、消息列表等）仍以 DB/Mongo 直读为主。
3. 结论：
   - 一致性风险低（交易主链路不依赖 Redis 读缓存）；
   - 性能侧仍有“可缓存但未缓存”的优化空间。

---

## 2. 缓存 Key 设计规范（冻结）

## 2.1 命名模板

统一模板：`cache:{domain}:{scene}:{bizKey}:{version}`

示例：
1. 商品详情：`cache:product:detail:{productId}:v1`
2. 商品列表：`cache:product:list:{queryHash}:v1`
3. 用户侧统计：`cache:user:summary:{userId}:v1`

## 2.2 设计规则

1. 必须带业务域（`product/order/user/stat`），禁止裸 key。
2. 列表查询必须对参数做归一化后再 hash（避免同义参数产生多 key）。
3. 保留版本位（`v1`），用于未来结构升级平滑切换。
4. 关键 key 长度建议 `< 128` 字符。
5. 禁止把敏感明文字段（手机号、邮箱、token）直接拼入 key。

---

## 3. TTL 口径（冻结）

| 缓存类型 | TTL 建议 | 抖动 | 说明 |
|---|---|---|---|
| 商品详情（稳定读多写少） | `120s` | `±20%` | 优先收益路径 |
| 商品列表（分页/筛选） | `30~60s` | `±20%` | 防热点查询穿库 |
| 用户首页摘要（非资金） | `30s` | `±20%` | 短 TTL 保持新鲜 |
| 验证码/令牌类 | 按安全口径（5m/24h） | 不建议抖动 | 安全语义优先 |
| 风控计数 | 按窗口口径（如 30m） | 不建议抖动 | 与策略窗口强绑定 |
| DAU 指标键 | 按天管理（建议离线清理） | 无 | 指标集合，不是业务读缓存 |

说明：
1. 抖动用于降低同一时刻批量过期导致的击穿风险。
2. 资金类、订单状态强一致读默认不做长 TTL 缓存。

---

## 4. 关键读路径缓存策略矩阵（P6-S3）

| 读路径 | 当前实现 | 缓存策略 | 失效触发 |
|---|---|---|---|
| `GET /user/market/products` 市场列表 | DB 直读 | `cache-aside` + 短 TTL（30~60s） | 商品上架/下架、库存/价格变化后删除相关列表缓存 |
| `GET /user/market/products/{productId}` 商品详情 | DB 直读 | `cache-aside` + TTL 120s | 商品编辑、状态变更、违规下架后删除详情缓存 |
| `GET /user/orders/{orderId}` 订单详情 | DB 直读 | 默认不缓存（强一致优先） | 不适用 |
| `GET /admin/statistics/dau` DAU 指标 | Redis set + DB 辅助 | 维持现状（指标缓存） | 按日期切换与历史清理 |

策略结论：
1. 关键读路径“有缓存策略”并不等于“全部必须缓存”。
2. 对强一致敏感路径（订单状态）明确采用“禁缓存/极短缓存”策略也是冻结结论的一部分。

---

## 5. 写后更新/删除策略（统一口径）

1. 默认采用 `cache-aside`：先提交 DB 事务，再删缓存。
2. 事务场景必须在 `afterCommit` 执行删缓存，禁止事务内先写缓存后写库。
3. 热点 key 允许使用“双删”策略：
   - 第一次：`afterCommit` 立即删除；
   - 第二次：延迟 `300~500ms` 再删一次（防并发旧值回填）。
4. 删除失败必须记录告警日志，进入重试队列或运维补偿清单。
5. 多 key 关联场景（详情 + 列表）优先删详情，再删列表模式 key。

---

## 6. 穿透/击穿/雪崩基础防护

## 6.1 穿透防护

1. 对不存在对象缓存空值（`null-object`），TTL 建议 `15~30s`。
2. 参数校验前置（ID 合法性、分页边界、枚举白名单）。

## 6.2 击穿防护

1. 热点 key 重建使用互斥锁（Redis `SET NX EX`）或单飞控制。
2. 锁等待超时后返回降级结果，避免线程堆积。

## 6.3 雪崩防护

1. TTL 抖动（`±20%`）避免同批 key 同时过期。
2. 限流与降级联动（参照 P6-S2 并发保护方案）。

---

## 7. 监控与告警口径（缓存维度）

1. 命中率：`hit_rate < 70%` 持续 5 分钟触发告警。
2. 缓存错误率：序列化/反序列化异常、连接超时超过阈值触发告警。
3. key 数量异常增长：按业务域统计（`cache:product:*` 等）做环比监控。
4. 热 key 识别：单 key QPS 异常抬升需进入治理清单。
5. 删除失败与重建超时：纳入审计与值班排障流程。

---

## 8. 复现与评审要求

1. 每个新增缓存点必须提供：
   - key 模板
   - TTL
   - 真源
   - 失效触发点
   - 失败回退路径
2. 必须提供至少 1 条复现实验：
   - 写后删缓存是否生效；
   - key 过期后是否按预期回源并重建。
3. 评审未通过项禁止上线。

---

## 9. DoD 对齐（P6-S3）

- [x] 已形成关键读路径缓存策略矩阵（含“应缓存/不缓存”边界）。  
- [x] 已冻结 key 设计、TTL、写后失效、穿透/击穿防护统一口径。  
- [x] 已定义缓存失效行为复现步骤与评审门槛。  
- [ ] 待后续按复现文档完成运行态动态验证并回填实测数据。  

---

## 10. 证据索引

1. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
2. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
3. `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`
4. `demo-service/src/main/java/com/demo/service/serviceimpl/StatisticsServiceImpl.java`
5. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
6. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
7. `demo-service/src/main/java/com/demo/config/RedisConfiguration.java`
8. `demo-service/src/main/resources/application.yml`
9. `day18回归/缓存一致性治理/Day18_P2_S2_缓存一致性规范_v1.0.md`
10. `day18回归/性能治理/Day18_P6_S2_并发保护方案_v1.0.md`

---

（文件结束）
