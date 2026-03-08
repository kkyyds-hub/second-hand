# Day17 P5-S4 注释补充记录 v1.0

- 日期：2026-02-24  
- 目的：把关键服务与 Mapper 的设计意图沉淀为“可读代码说明”，降低新人接手成本。  
- 范围：Day17 核心链路（Outbox、超时任务、提醒任务、退款任务）。

---

## 1. 本次新增/优化注释清单

| 序号 | 文件 | 补充点 | 目的 |
|---|---|---|---|
| 1 | `demo-service/src/main/java/com/demo/service/serviceimpl/OutboxServiceImpl.java` | `save` 方法事务语义注释（MANDATORY + 同提交同回滚） | 明确“主事务内落 Outbox”约束 |
| 2 | `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java` | `createReminderTasksForPaidOrder` 注释细化 | 明确 H24/H6/H1 建任务规则与幂等口径 |
| 3 | `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java` | `processDueTasks` 批处理顺序注释细化 | 明确回收/抢占/单条处理三段式执行模型 |
| 4 | `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java` | 删除无效模板注释并保留语义化代码注释 | 去除噪音，聚焦事务与 CAS 分流逻辑 |
| 5 | `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java` | 删除无效模板注释并保留语义化代码注释 | 去除噪音，聚焦 afterCommit 与幂等分流 |

---

## 2. 关键文件阅读顺序（给接手同学）

建议按“主链路 -> 异步 -> 任务补偿”顺序阅读：

1. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`  
2. `demo-service/src/main/java/com/demo/service/serviceimpl/OutboxServiceImpl.java`  
3. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`  
4. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`  
5. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java`  
6. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java`

---

## 3. Mapper 说明覆盖结果

## 3.1 已具备详细注释的核心 Mapper

1. `demo-service/src/main/java/com/demo/mapper/MessageOutboxMapper.java`  
2. `demo-service/src/main/java/com/demo/mapper/OrderShipReminderTaskMapper.java`  
3. `demo-service/src/main/java/com/demo/mapper/OrderRefundTaskMapper.java`  
4. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`  
5. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`  
6. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`

## 3.2 注释关注点（统一口径）

1. 该 SQL/方法服务于哪个业务动作。  
2. 输入参数和幂等键是什么。  
3. 是否依赖 CAS 条件（`expectedStatus`）。  
4. 是否依赖索引、是否为高频路径。  
5. 失败/并发未命中时如何分流记录日志。

---

## 4. 注释规范（后续新增代码必须遵守）

1. 先写“为什么”，再写“做什么”。  
2. 不写空泛注释（如“实现接口方法”）。  
3. 日志与注释语言统一中文，字段名保持英文。  
4. 对事务、幂等、并发分流必须有显式说明。  
5. XML 复杂 SQL 必须注明用途/输入/索引依赖。

---

## 5. 剩余改进建议（非阻断）

1. 对 `OrderServiceImpl` 的长方法可再拆分注释分段（下单、支付、发货、完结）。  
2. 对 `ProductServiceImpl` 这类超长类建议增加“方法组导航注释”。  
3. 后续可补一份“异常码与分流日志对照表”帮助排障。

---

（文件结束）
