# Day18 P2-S2 缓存一致性规范 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P2-S2：缓存一致性策略落地`
- 目标：确保缓存与数据库的数据变更路径可解释、可审计、可复现。

---

## 1. 适用范围

1. 当前工程 Redis 使用点（`StringRedisTemplate`）：
   - `AuthServiceImpl`
   - `UserServiceImpl`
   - `JwtTokenUserInterceptor`
   - `StatisticsServiceImpl`
2. 本规范同时约束后续新增缓存点的设计与评审。

---

## 2. Redis 键模型与失效策略（现状基线）

| 键前缀 | 类型 | TTL | 生产位置 | 消费位置 | 一致性语义 |
|---|---|---|---|---|---|
| `auth:sms:code:{mobile}` | string | 5 分钟 | `sendSmsCode` | 注册/绑手机/改密校验 | 短期验证码，超时自然失效 |
| `auth:sms:rate:{mobile}` | string | 1 分钟 | `sendSmsCode` | 发送频控 | 频率限制窗口键 |
| `auth:email:activation:{token}` | string | 24 小时 | `sendActivationMail` | `activateEmail` | 激活令牌，使用后删除 |
| `auth:login_fail:{userId}` | string | 30 分钟（首失败设定） | 登录失败计数 | 风控冻结判断 | 窗口计数，登录成功删除 |
| `dau:{yyyy-MM-dd}` | set | 无 TTL（`-1`） | 用户 GET 接口拦截器写入 | 统计接口读取 | 当日去重活跃集合（指标数据） |
| `auth:oauth:{provider}:{externalId}` | string | 无 TTL | 第三方登录落库后写入 | 第三方登录优先命中 | 外部账号映射（更接近映射存储而非缓存） |

说明：
1. 当前未发现 `@Cacheable/@CacheEvict` 的业务读缓存实现。
2. 订单、商品、支付主链路不依赖 Redis 作为读缓存，不存在“DB 已更新但读缓存长期不刷新”的核心交易风险。

---

## 3. 关键写链路的一致性顺序

## 3.1 手机验证码链路

1. 发送验证码：
   - 写 `auth:sms:code`（TTL=5m）
   - 写 `auth:sms:rate`（TTL=1m）
2. 使用验证码（注册/绑定等）：
   - 先校验缓存验证码
   - 再更新 DB
   - 最后删除验证码键（及频控键）

结论：采用“校验缓存 -> 提交 DB -> 删除缓存”的单向收敛路径，不会产生长期脏缓存。

## 3.2 邮箱激活链路

1. 发送激活邮件时写入 `auth:email:activation:{token}`（TTL=24h）。
2. 激活时：
   - 校验 token
   - 更新 DB 状态（`inactive -> active`）
   - 删除 token 键

结论：激活令牌是一次性流程状态，使用后删除，TTL 兜底。

## 3.3 登录风控链路

1. 登录失败：`auth:login_fail:{userId}` 计数+1；首次失败设置 TTL=30m。
2. 登录成功：删除失败计数键。
3. 失败达到阈值触发 DB 冻结状态更新（DB 为状态真源）。

结论：Redis 仅承载窗口计数，最终账户状态以 DB 为准，不存在长期状态漂移。

## 3.4 DAU 指标链路

1. 用户访问 `GET /user/**` 时写入 `dau:{date}` set。
2. 管理端统计读取 set 基数。

结论：`dau:*` 属指标集合，不是业务读缓存；无“脏读”问题，但存在容量增长风险，需按运维策略清理历史键。

---

## 4. 一致性风险与规避规则

## 4.1 已识别风险

1. `dau:*` 无 TTL，长期运行会累积历史键（容量风险）。
2. `auth:oauth:*` 无 TTL，属于映射存储；若未来引入解绑/换绑流程，需同步定义失效策略。
3. 改密流程使用验证码后未主动删除验证码键（在 TTL 内可重复使用，属于安全收敛风险）。

## 4.2 规避规则（冻结）

1. 核心交易链路禁止把 Redis 作为唯一事实源（DB 必须可回放）。
2. 新增缓存键必须定义：
   - key 命名规范
   - 类型
   - TTL/失效条件
   - 真源（DB/MQ/外部系统）
   - 失效触发点
3. 涉及 DB 更新的读缓存，默认采用 `cache-aside`：
   - 先提交 DB 事务
   - `afterCommit` 删除缓存
   - 禁止事务内先写缓存后写 DB
4. 验证码/令牌类键必须“一次性语义优先”，成功消费后应删除。
5. DAU 类长期指标键须制定保留策略（如按月归档或定时清理）。

---

## 5. 新增缓存点评审清单（强制）

1. 这个键的“真源”是什么？缓存失效后能否从真源重建？
2. TTL 是否明确？为什么是这个值？
3. DB 写链路中的删缓存时机是否为 `afterCommit`？
4. 并发下是否可能出现旧值回填（cache stampede / stale writeback）？
5. 是否有监控指标（命中率、键数量、异常膨胀）？
6. 是否有灰度开关和降级路径？

---

## 6. 评审问题答复（当前基线，2026-02-26）

| 评审问题 | 当前结论 | 代码依据/风险 | 改进动作（冻结） |
|---|---|---|---|
| 1) 真源是什么，失效后能否重建？ | 分类型结论：`auth:sms:*`/`auth:email:activation:*`/`auth:login_fail:*` 真源是“业务过程状态”；`dau:*` 真源是 Redis 指标集合；`auth:oauth:*` 当前更接近“映射存储”而非可丢缓存。 | `AuthServiceImpl`、`JwtTokenUserInterceptor`、`StatisticsServiceImpl`。其中 `auth:oauth:*` 缺少独立持久化映射表，Redis 丢失后无法按“稳定映射”重建。 | 后续将第三方账号映射下沉到 DB 表，Redis 仅作加速缓存。 |
| 2) TTL 是否明确，为什么是这个值？ | 现有键 TTL 基本明确：验证码 5m、频控 1m、激活 24h、登录失败窗口 30m；`dau:*` 与 `auth:oauth:*` 当前无 TTL。 | TTL 与安全/风控窗口一致；无 TTL 两项存在容量与治理风险。 | 为 `dau:*` 增加保留/清理策略；为 `auth:oauth:*` 增加生命周期治理（或迁移到 DB 真源）。 |
| 3) DB 写链路删缓存是否 `afterCommit`？ | 对“业务读缓存”场景：当前项目基本未落地，暂无直接冲突；对验证码/令牌删除：多数为方法内即时删除，不是 `afterCommit` 回调。 | `UserServiceImpl` 绑定/解绑流程在事务内先更新 DB 再删 key；当前未统一 `afterCommit` 删除封装。 | 冻结规则维持：后续新增“读缓存”必须 `afterCommit` 删除；现有验证码/令牌链路纳入专项评审，必要时补事务后置删除。 |
| 4) 并发下是否会旧值回填？ | 当前无大规模业务读缓存，旧值回填风险整体较低；但未来若引入商品列表/详情缓存，需防击穿与旧值回填。 | 现状主要是流程键（验证码、计数器、DAU），不是“DB->Cache”双写模型。 | 新增读缓存必须采用 `cache-aside + afterCommit +（可选）双删`，并定义热点 key 互斥重建策略。 |
| 5) 是否有监控指标？ | 当前无统一缓存命中率/键数量/异常膨胀看板，主要依赖临时命令核查。 | 现有 P4-S2 告警重点在 Outbox/任务/MQ，缓存指标尚未体系化。 | 增加缓存监控最小集：`hit/miss`、key 数量增长、异常 TTL 分布、Redis 异常率。 |
| 6) 是否有灰度开关和降级路径？ | 现有仅有全局级配置 `demo.redis.enabled`，缺少“按业务缓存点”灰度开关与标准降级策略。 | `RedisConfiguration` 受 `demo.redis.enabled` 控制；未见细粒度缓存开关。 | 新增缓存点必须提供：功能开关、降级回源策略、失败语义（fail-open/fail-closed）与回滚方案。 |

结论：
1. 当前缓存一致性风险总体可控（交易主链路未依赖 Redis 读缓存）。
2. 仍有三项需跟踪：`auth:oauth:*` 真源治理、缓存监控指标体系、细粒度灰度/降级能力。

---

## 7. 动态验证摘要（2026-02-25）

执行结果（测试库/本地 Redis DB=2）：
1. `POST /user/auth/sms/send` 后：
   - `auth:sms:code:13800990098` 存在，TTL=`300`
   - `auth:sms:rate:13800990098` 存在，TTL=`60`
2. 用户登录后访问 `GET /user/orders/buy`：
   - `dau:2026-02-25` 中 `userId=1` 为成员（`SISMEMBER=1`）
3. 密码登录失败一次：
   - `auth:login_fail:1` 值=`1`，TTL=`1800`
4. 随后登录成功：
   - `auth:login_fail:1` 被删除（`EXISTS=0`）

结论：
1. 已验证关键键模型与 TTL 行为符合代码口径。
2. 关键链路未发现“长期脏缓存”已知风险（交易主链路不依赖 Redis 读缓存）。

---

## 8. DoD 对齐（P2-S2）

- [x] 已梳理 Redis 键模型与失效策略。  
- [x] 已明确关键写链路缓存更新/删除顺序。  
- [x] 已补充缓存一致性风险与规避规则。  
- [x] 关键链路无“长期脏缓存”已知风险。  
- [x] 新增缓存点已形成规范化评审清单。  

---

## 9. 代码证据索引

1. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
2. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
3. `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`
4. `demo-service/src/main/java/com/demo/service/serviceimpl/StatisticsServiceImpl.java`
5. `demo-service/src/main/java/com/demo/config/RedisConfiguration.java`
6. `demo-service/src/main/resources/application.yml`
7. `demo-service/src/main/resources/application-dev.yml`

---

（文件结束）
