# Day15 Step7 执行计划：发货超时提醒（站内信）

- 关联冻结文档：`day15回归/Day15_Scope_Freeze_v1.0.md`
- 计划版本：v1.0
- 制定日期：2026-02-12
- 本步目标：完成“卖家发货超时提醒”闭环，具备可重试、可观测、可回放、可幂等能力。

---

## 0. 本步对应冻结文档位置

1. `9.3 触发时机`：临近发货超时提醒卖家（24h/6h/1h）
2. `9.4 通知模板`：超时提醒模板落地
3. `15.4 通知与异步`：三类通知之一（临近超时提醒）必须可见
4. `14.1 管理员运维工具`：支持手动跑批与补偿

---

## 1. 已确认技术决策（本次固定）

1. 数据库版本：MySQL `8.0`
2. Job 扫描周期：`1 分钟`
3. 批量处理上限：`200` 条/轮
4. RUNNING 过期回收阈值：`5 分钟`
5. 失败重试策略：指数退避档位 `2m / 5m / 15m / 30m`
6. 提醒对象：仅卖家
7. 提醒级别：`H24 / H6 / H1`
8. 提醒文案：固定模板 + 动态“剩余时间”
9. 任务驱动模式：纯 Job 定时扫描执行
10. 幂等主键策略：任务表唯一约束 + `clientMsgId` 唯一幂等

---

## 2. 业务目标与边界

### 2.1 业务目标
1. 支付后未发货订单，在接近 48h 截止点时给卖家发送提醒。
2. 同一订单同一档位只发一次，不重复刷屏。
3. 网络抖动或消息写入失败时，可自动重试并最终补发。
4. 订单已发货/已完成/已取消时，提醒任务自动跳过。

### 2.2 边界限制
1. 本步只做站内信，不接短信/Push。
2. 本步只覆盖 `ship_timeout` 相关提醒，不扩展其他营销通知。
3. 本步不改主交易状态机，只新增提醒侧异步能力。

---

## 3. 数据模型设计

## 3.1 新增任务表：`order_ship_reminder_task`

建议字段：
1. `id` BIGINT PK
2. `order_id` BIGINT NOT NULL
3. `seller_id` BIGINT NOT NULL
4. `level` VARCHAR(8) NOT NULL
5. `deadline_time` DATETIME NOT NULL
6. `remind_time` DATETIME NOT NULL
7. `status` VARCHAR(16) NOT NULL
8. `retry_count` INT NOT NULL DEFAULT 0
9. `running_at` DATETIME NULL
10. `sent_at` DATETIME NULL
11. `client_msg_id` VARCHAR(128) NULL
12. `last_error` VARCHAR(255) NULL
13. `create_time` DATETIME NOT NULL
14. `update_time` DATETIME NOT NULL

状态建议：
1. `PENDING`：待执行
2. `RUNNING`：已抢占，处理中
3. `SUCCESS`：发送成功（或幂等命中视作成功）
4. `FAILED`：本次失败，等待下次重试窗口
5. `CANCELLED`：订单状态已无须提醒

索引与约束：
1. `UNIQUE(order_id, level)`
2. `INDEX idx_status_remind_time(status, remind_time)`
3. `INDEX idx_status_running_at(status, running_at)`

说明：
1. `level` 采用 `VARCHAR(H24/H6/H1)`，可读性高且便于日志检索。
2. 不单独增加 `next_retry_time`，直接复用 `remind_time` 表达下次可执行时间。

---

## 4. 任务产生策略

## 4.1 预生成策略（支付成功时一次生成 3 条）

支付成功后立即预生成：
1. `H24`：`remind_time = deadline_time - 24h`
2. `H6`：`remind_time = deadline_time - 6h`
3. `H1`：`remind_time = deadline_time - 1h`

规则：
1. 使用 `INSERT IGNORE` 或唯一冲突忽略，确保幂等。
2. 如果 `remind_time <= now`，保留原值，交由 Job “到期即执行”。
3. 任务初始化 `status=PENDING`。

---

## 5. Job 执行流程设计

## 5.1 Job 入口

`OrderShipReminderTaskJob` 每 1 分钟执行一次：
1. 回收超时 RUNNING 任务
2. 抢占到期任务（最多 200 条）
3. 逐条执行发送
4. 成功/失败落状态

## 5.2 RUNNING 回收（5 分钟）

回收条件：
1. `status=RUNNING`
2. `running_at <= now - 5 minutes`

回收动作：
1. 更新为 `FAILED`
2. `retry_count = retry_count + 1`
3. `remind_time = now + backoff(retry_count)`
4. 记录 `last_error='running_timeout_recycle'`

## 5.3 任务抢占（MySQL 8.0）

使用批量条件更新抢占，避免并发节点重复处理：
1. 条件：`status in (PENDING, FAILED)` 且 `remind_time <= now`
2. 排序：`remind_time asc, id asc`
3. 限制：`limit 200`
4. 更新：`status=RUNNING, running_at=now`

随后按 `status=RUNNING` + 本轮标记查询本批任务明细进行处理。

## 5.4 单条处理逻辑

1. 查询订单提醒专用视图：`selectOrderForReminder(orderId)`
2. 订单终态拦截：若 `SHIPPED/COMPLETED/CANCELLED`，任务置 `CANCELLED`
3. 计算动态剩余时间：`deadline_time - now`
4. 组装文案：固定模板 + 剩余时间 + 订单号
5. 生成 `clientMsgId`：`SHIP-REMIND-{yyyyMMdd}-{level}-{orderId}`
6. 调用 `MessageService` 发送（或写 outbox 后异步投递）
7. 成功则 `SUCCESS + sent_at + client_msg_id`
8. 失败则 `FAILED + retry_count+1 + remind_time=now+backoff`

## 5.5 重试档位

指数退避映射：
1. 第 1 次失败：`+2m`
2. 第 2 次失败：`+5m`
3. 第 3 次失败：`+15m`
4. 第 4 次及以上：`+30m`

建议上限：`max_retry=8`，超过后保持 `FAILED`，由运维接口人工介入。

---

## 6. 幂等与一致性策略

1. 任务创建幂等：`UNIQUE(order_id, level)`
2. 发送幂等：`clientMsgId` 唯一约束，重复写入视为成功
3. 处理幂等：任务状态机只允许
   - `PENDING/FAILED -> RUNNING`
   - `RUNNING -> SUCCESS/FAILED/CANCELLED`
4. 业务兜底：发送前再次检查订单状态，防止“已发货后仍提醒”

---

## 7. 建议代码落点（按你当前工程分层）

1. 实体：`demo-pojo/src/main/java/com/demo/entity/OrderShipReminderTask.java`
2. Mapper：`demo-service/src/main/java/com/demo/mapper/OrderShipReminderTaskMapper.java`
3. XML：`demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
4. Service：`demo-service/src/main/java/com/demo/service/OrderShipReminderTaskService.java`
5. ServiceImpl：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java`
6. Processor：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskProcessor.java`
7. Job：`demo-service/src/main/java/com/demo/job/OrderShipReminderTaskJob.java`
8. 订单查询专用 SQL：`OrderMapper.xml` 新增 `selectOrderForReminder`
9. 运维接口扩展：`demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`

---

## 8. 配置项清单（application.yml）

```yaml
order:
  ship-reminder:
    enabled: true
    fixed-delay-ms: 60000
    batch-size: 200
    running-timeout-minutes: 5
    max-retry: 8
    backoff-minutes: 2,5,15,30
```

说明：
1. `batch-size=200` 在你当前项目规模下是合理起点。
2. 若后续单轮耗时超过 60s，可调小 batch 或增加并发分片。

---

## 9. 测试与验收计划

## 9.1 单元测试
1. backoff 计算正确
2. 剩余时间文案格式正确
3. 状态机非法流转被拒绝
4. 重复发送 `clientMsgId` 被幂等拦截

## 9.2 集成测试
1. 支付后自动生成 3 条提醒任务
2. 到点后正确发送并置 `SUCCESS`
3. 发送异常后进入 `FAILED` 并按退避重试
4. 订单已发货后任务自动 `CANCELLED`
5. RUNNING 卡死任务 5 分钟后被回收

## 9.3 回归验收口径
1. 卖家可收到 H24/H6/H1 三档提醒（模拟时间）
2. 同档位不重复发送
3. 网络抖动后可补发
4. 管理员接口可查询与手动触发处理

---

## 10. 实施顺序（建议 1.5~2 天）

1. D1 上午：建表 + Mapper + 基础 Service
2. D1 下午：Processor + Job 主流程 + 状态机
3. D2 上午：消息发送幂等 + 文案动态剩余时间
4. D2 下午：联调测试 + 运维接口补齐 + 回归脚本

---

## 11. 风险与对策

1. 风险：Job 多实例并发重复发送
   - 对策：抢占更新 + `clientMsgId` 唯一幂等双保险
2. 风险：消息存储偶发失败导致丢提醒
   - 对策：失败状态重试 + backoff + 运维手动补偿
3. 风险：提醒发送晚于目标时间
   - 对策：1 分钟扫描 + 到期即发 + 动态剩余时间实时计算

---

## 12. 本步完成定义（DoD）

1. `order_ship_reminder_task` 表已上线并带唯一约束
2. 支付成功可稳定生成 H24/H6/H1 三档任务
3. Job 可批量执行并正确推进任务状态
4. 失败任务按 `2/5/15/30` 分钟退避重试
5. 消息发送具备 `clientMsgId` 幂等能力
6. 管理员可查询并手动补偿
7. 回归用例全部通过

---

（文件结束）
