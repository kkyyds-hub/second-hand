# Day18 现状基线与差距清单 v1.0

- 日期：2026-02-24  
- 目的：基于当前仓库代码，给出 Day18 一致性与安全收口的“事实基线 + 差距列表”。  
- 适用范围：`demo-service`、`demo-common`、`demo-pojo` 及 Day17 既有文档资产。

---

## 1. 评估结论总览

1. **已具备较完整基础**：事务边界、Outbox、消费幂等、任务补偿、并发分流基础单测。  
2. **部分落地**：安全防护、风控策略、监控告警、缓存一致性规范。  
3. **待补齐重点**：统一 XSS/CSRF 策略、异常交易规则化、限流方案、安全扫描与日志平台接入规范。

---

## 2. 能力矩阵（按 Day18 七大主题）

| 主题 | 当前代码证据 | 现状判定 | Day18 收口动作 |
|---|---|---|---|
| 事务一致性 | `OrderServiceImpl`、`OrderShipTimeoutTaskProcessor`、`OrderRefundTaskProcessor` 大量 `@Transactional`；`afterCommit` 通知 | 已落地（核心链路） | 统一事务边界文档与异常分流说明 |
| 分布式事务策略 | 未检出 Seata/Saga/TCC 框架依赖 | 未引入框架（边界明确） | 固化“本地事务+Outbox+补偿”作为 Day18 主方案 |
| 幂等设计 | `insertIgnore` + 唯一键、CAS 更新、幂等命中日志；`mq_consume_log` 去重 | 已落地（核心链路） | 补幂等键总表与接口级验收脚本 |
| 失败重试 | 任务表 `retry_count/next_retry_time`、Outbox 失败重试回写 | 已落地（任务链路） | 统一重试参数阈值与失败分类标准 |
| 最终一致性 | `OutboxPublishJob` + `OutboxBatchStatusService` + `OutboxMonitorJob` | 已落地（异步链路） | 增强运维恢复手册与演练记录 |
| 分布式缓存一致性 | Redis 已使用（登录失败计数、验证码、DAU 等），但未见统一双写/失效规范文档 | 部分落地 | 输出缓存一致性规范并锁定关键写链路策略 |
| 乐观/悲观锁 | 关键状态更新广泛使用条件更新/CAS；未见悲观锁统一策略 | 部分落地 | 补“何时可引入悲观锁”规则与禁用清单 |
| 敏感数据加密 | BCrypt 密码加密、JWT 拦截、改密/绑定二次验证 | 部分落地 | 收口密钥治理、日志脱敏、环境配置规范 |
| HTTPS 传输 | 应用配置未强制 TLS（通常由网关层承接） | 架构外部依赖 | 文档化“网关强制 HTTPS”验收项 |
| SQL 注入防护 | MyBatis 参数化、`OrderMapper.xml` 排序白名单、MP `BlockAttackInnerInterceptor` | 已落地（主链路） | 增加审计清单，覆盖高风险动态查询点 |
| XSS 防护 | 发现敏感词与长度校验，但未见统一 XSS 过滤组件 | 部分落地 | 定义输入/输出统一 XSS 规则并在高风险入口落地 |
| CSRF 防护 | 当前以 JWT Header 鉴权为主，未检出 CSRF Token 机制 | 需边界说明 | 输出 JWT 架构下 CSRF 风险评估与替代策略 |
| 日志审计 | 核心链路日志较完整（事件ID、幂等命中、任务状态） | 部分落地 | 统一审计字段字典与日志模板 |
| 监控告警 | Outbox 已有阈值监控与 ERROR 告警 | 部分落地 | 扩展到任务重试、异常交易、安全事件 |
| ELK/Kibana | 未检出相关依赖与部署脚本 | 未落地 | 产出接入方案与字段规范（Day18 不部署） |
| 风控与信用 | `CreditServiceImpl`、`AuthServiceImpl`、`ProductServiceImpl` 已有信用/冻结/发布限制 | 部分落地 | 形成规则手册并补异常交易规则 |
| 异常交易监控 | 未见“大额/频繁退款”等规则引擎化实现 | 待补齐 | 补最小可用规则集 + 人工审核入口 |
| 性能优化 | Day17 已有索引治理与慢 SQL 文档资产 | 已有基础 | Day18 做回归与新增热点接口并发保护 |
| 限流控制 | 未检出统一限流组件（网关/应用） | 待补齐 | 输出限流与降级方案（先文档后落地） |
| 回归测试 | Day17 Newman 全量回归通过；并发单测已存在 | 部分落地 | 扩 Day18 用例并固化安全扫描流程 |
| 安全渗透测试 | 未检出 ZAP 扫描资产 | 待补齐 | 增 ZAP baseline 执行步骤和报告模板 |

---

## 3. 关键证据清单（摘录）

1. 事务与补偿：
   - `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
   - `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`
   - `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java`
2. 异步一致性：
   - `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
   - `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
   - `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
3. 消费端幂等：
   - `demo-service/src/main/java/com/demo/mq/consumer/OrderPaidConsumer.java`
   - `demo-service/src/main/resources/mapper/MqConsumeLogMapper.xml`
4. 安全基线：
   - `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java`
   - `demo-service/src/main/resources/mapper/OrderMapper.xml`
   - `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
   - `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
5. 风控与策略：
   - `demo-service/src/main/java/com/demo/service/serviceimpl/CreditServiceImpl.java`
   - `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
6. 测试资产：
   - `demo-service/src/test/java/com/demo/concurrency/OrderShipTimeoutTaskProcessorConcurrencyTest.java`
   - `demo-service/src/test/java/com/demo/concurrency/OrderRefundTaskProcessorConcurrencyTest.java`
   - `day17回归/执行记录/Day17_Newman_执行记录_v1.0.md`

---

## 4. Day18 P0 优先级（先做这批）

1. 事务/幂等/重试口径统一文档（含复现步骤）。  
2. 缓存一致性规范与关键链路策略落地。  
3. 安全收口：密钥治理、日志脱敏、XSS 统一规则、CSRF 边界说明。  
4. 审计日志字段字典 + 告警规则扩展（Outbox 之外覆盖任务与风险事件）。  
5. 异常交易最小规则集（大额交易、频繁退款、异常取消）。  
6. 回归扩展：Day18 Newman 用例 + ZAP baseline 扫描流程。

---

## 5. 非目标（本阶段不做）

1. 引入 Seata/Saga/TCC 等新分布式事务框架。  
2. 数据库读写分离或分库分表改造。  
3. ELK/Kibana 实际部署与生产运维落地。  
4. 全量重写历史模块以追求“技术一致”而非“业务稳定”。

---

（文件结束）
