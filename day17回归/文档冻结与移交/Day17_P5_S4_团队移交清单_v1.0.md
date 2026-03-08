# Day17 P5-S4 团队移交清单 v1.0

- 日期：2026-02-24  
- 目的：让新成员在 1 小时内理解 Day17 改造边界并可继续迭代。

---

## 1. 移交前置条件

1. 代码分支包含 Day17 全量改造提交（P1~P5）。  
2. 数据库已执行 Day17 索引与唯一约束脚本。  
3. 本地可运行 Day17 Newman 回归集合。  
4. 关键文档路径已固定，不再散落到根目录。

---

## 2. 必读文档（顺序）

1. `day17回归/Day17_Scope_Freeze_v1.0.md`  
2. `day17回归/试点迁移评估/Day17_P2_S1_迁移优先级表_v1.0.md`  
3. `day17回归/慢SQL与索引治理/Day17_P3_S3_SQL优化清单_v1.0.md`  
4. `day17回归/事务治理/Day17_P4_S1_事务边界清单_v1.0.md`  
5. `day17回归/并发治理/Day17_P4_S3_并发安全改造清单_v1.0.md`  
6. `day17回归/文档冻结与移交/Day17_P5_S4_数据库设计文档_v1.0.md`  
7. `day17回归/文档冻结与移交/Day17_P5_S4_MP使用规范_v1.0.md`

---

## 3. 新成员首日操作清单

1. 跑一次 Day17 Newman 全量回归，确认本地基础环境。  
2. 按 MP 规范抽查 2 个模块，确认未出现分页混用。  
3. 抽查 2 条关键 SQL 的 EXPLAIN 文档，确认索引证据可追溯。  
4. 走一遍“订单超时 -> 退款任务 -> Outbox 发布”链路日志。  
5. 在 `执行复现步骤` 目录完成一次复现并留下执行记录。

---

## 4. 接口与链路责任归属

1. **主交易链路**：`OrderServiceImpl`（下单/支付/发货/完结/取消）。  
2. **异步可靠投递**：`OutboxServiceImpl` + `OutboxPublishJob`。  
3. **发货超时补偿**：`OrderShipTimeoutTaskProcessor`。  
4. **发货提醒任务**：`OrderShipReminderTaskServiceImpl` + `OrderShipReminderTaskProcessor`。  
5. **退款任务处理**：`OrderRefundTaskProcessor`。  
6. **消费端幂等**：`mq_consume_log` 唯一键 + 各 Consumer 幂等分流。

---

## 5. 交付验收清单（DoD 对照）

| 验收项 | 判定标准 | 证据 |
|---|---|---|
| 文档可读 | 新人可按文档定位改造边界 | 本目录 + Scope Freeze |
| 规范可执行 | MP/XML 边界可直接用于评审 | `Day17_P5_S4_MP使用规范_v1.0.md` |
| 数据可追溯 | 关键表结构与约束有据可查 | `Day17_P5_S4_数据库设计文档_v1.0.md` |
| 代码可接手 | 核心服务/Mapper 有说明注释 | `Day17_P5_S4_注释补充记录_v1.0.md` |
| 回归可复现 | Newman 命令可一键执行 | `day17回归/执行记录/Day17_Newman_执行记录_v1.0.md` |

---

## 6. 后续迭代建议（Day18+）

1. 在 CI 固化 Sonar 扫描与 Quality Gate 阈值。  
2. 对超长 Service 类做职责拆分（按命令/查询分离）。  
3. 针对 Outbox 积压场景补充压测基线与报警阈值。  
4. 增加“数据库变更申请模板”，要求每次变更附索引与回滚语句。

---

（文件结束）
