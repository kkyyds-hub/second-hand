# Day18 P4-S3 日志平台接入指南 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P4-S3：日志平台接入方案（文档化）`
- 文档定位：**给第一次接触 ELK 的同学使用**，按步骤可直接实施。
- 重要边界：本指南为接入方案文档，**Day18 不做实际生产部署**。

---

## 0. 你先知道这 3 句话

1. ELK 不是你自己写的平台，是业界现成组件组合。
2. 你们要做的是“接入和治理”（字段、索引、保留、脱敏、告警）。
3. 接入必须“不影响现有运行路径”：日志采集失败不能拖垮业务服务。

---

## 1. ELK 到底是什么（零基础版）

1. `Elasticsearch`：存储日志，支持检索和聚合。
2. `Logstash`：日志中转与清洗（可选）。
3. `Kibana`：可视化查询和看板。
4. 常见补充：`Filebeat` 负责采集应用日志文件并发送到 ES/Logstash。

一句话理解：
1. 应用写日志 -> Filebeat 收日志 -> Elasticsearch 存日志 -> Kibana 看日志。

---

## 2. 本项目接入目标（P4-S3 冻结）

1. 统一日志字段：至少包含 `traceId/eventId/bizId/status`。
2. 统一索引命名、保留策略、脱敏规则。
3. 给出可执行接入步骤，但不改当前主业务路径。
4. 与 P4-S1/P4-S2 联动：
   - P4-S1 提供 `AUDIT` 统一字段口径；
   - P4-S2 提供告警阈值与排障流程。

---

## 3. 当前项目现状（实施前基线）

1. 已有统一审计日志：`AUDIT auditId=... action=... result=...`。
2. 已有 Outbox/任务/MQ 关键运行日志与运维接口。
3. 当前应用默认以控制台日志为主，未冻结独立 ELK 采集链路。

当前代码证据：
1. `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java`
2. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
3. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
4. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`

---

## 4. 统一日志字段规范（必须执行）

## 4.1 通用字段（所有日志）

| 字段 | 必填 | 示例 | 说明 |
|---|---|---|---|
| `@timestamp` | 是 | `2026-02-25T18:00:00+08:00` | 事件时间 |
| `level` | 是 | `INFO` | 日志级别 |
| `service` | 是 | `demo-service` | 服务名 |
| `env` | 是 | `dev/test/prod` | 环境 |
| `host` | 是 | `app-01` | 主机名 |
| `message` | 是 | `Outbox 监控指标...` | 原始消息 |
| `status` | 是 | `SUCCESS/FAIL/IDEMPOTENT` | 统一状态语义 |

## 4.2 追踪字段（P4-S3 核心）

| 字段 | 必填 | 来源建议 | 说明 |
|---|---|---|---|
| `traceId` | 是（新增日志建议） | 网关/拦截器生成并透传 | 一次请求全链路追踪 |
| `eventId` | 条件必填 | Outbox/MQ 事件 ID | 异步消息链路追踪 |
| `bizId` | 条件必填 | 订单 ID / 工单 ID / 用户 ID | 业务对象追踪 |
| `action` | 条件必填 | 审计/任务动作名 | 如 `ORDER_PAY` |
| `errorCode` | 失败建议 | 业务异常码 | 失败聚类 |

## 4.3 审计字段（P4-S1 对齐）

| 字段 | 必填 | 说明 |
|---|---|---|
| `auditId` | 是（审计日志） | 单次审计动作唯一 ID |
| `actorType/actorId` | 是 | 操作主体 |
| `targetType/targetId` | 是 | 操作对象 |
| `ip` | 是 | 来源 IP |
| `detail` | 否 | 补充说明（禁止敏感明文） |

---

## 5. 索引命名规范（可直接落地）

推荐按“日志类型 + 环境 + 日期”分索引：

1. 业务运行日志：`demo-app-{env}-yyyy.MM.dd`
2. 审计日志：`demo-audit-{env}-yyyy.MM.dd`
3. 消息与任务日志：`demo-mqtask-{env}-yyyy.MM.dd`
4. 安全日志：`demo-security-{env}-yyyy.MM.dd`

示例：
1. `demo-app-test-2026.02.25`
2. `demo-audit-prod-2026.02.25`

为什么这样分：
1. 查询快（按类型与环境缩小范围）。
2. 保留策略可分开（审计日志保留更久）。
3. 权限隔离方便（审计索引可单独授权）。

---

## 6. 保留策略（ILM）建议

最小建议（可按合规再延长）：

1. `app` 业务日志：30 天
2. `mqtask` 消息任务日志：90 天
3. `audit` 审计日志：180 天
4. `security` 安全日志：180 天

滚动建议：
1. 按天滚动（`yyyy.MM.dd`）。
2. 单索引超过 20GB 可提前滚动（按后续容量评估调）。

---

## 7. 脱敏规则（必须，不能谈判）

## 7.1 禁止明文入库字段

1. 密码（明文/密文都不应落日志详情）
2. JWT 全串、刷新令牌、API 密钥
3. 验证码、激活码
4. 银行卡号、身份证号等高敏信息

## 7.2 脱敏格式建议

1. 手机：`138****0001`
2. 邮箱：`a***@test.com`
3. token：只保留前后片段（如 `abc123...9xyz`）
4. IP（可选）：内网全量、公网按安全策略脱敏

## 7.3 执行优先级

1. 应用侧优先脱敏（最安全）。
2. 采集侧二次兜底（Filebeat/Logstash 过滤）。
3. Kibana 展示层不做“首次脱敏”，只做补充隐藏。

---

## 8. 接入架构建议（不影响当前运行）

## 8.1 推荐方案（稳妥）

1. `应用` -> 输出日志（现有方式不变）
2. `Filebeat` -> 采集日志（异步）
3. `Logstash`（可选）-> 字段清洗与补全
4. `Elasticsearch` -> 入库
5. `Kibana` -> 检索/看板

## 8.2 为什么不会影响业务

1. 采集是旁路，不阻塞主请求。
2. Elasticsearch 不可用时，业务服务仍可正常处理请求。
3. 仅增加运维组件，不改变核心交易事务路径。

---

## 9. 傻瓜式实施步骤（后续团队按此执行）

## 步骤 1：准备环境

1. 准备三台或三容器：`elasticsearch`、`kibana`、`filebeat`（可选 `logstash`）。
2. 保证时间同步（NTP），否则时间轴混乱。
3. 先在测试环境落地，禁止第一天直接上生产。

## 步骤 2：确定采集源

1. 优先采集应用日志文件（如果只有控制台，先在运行容器层转文件）。
2. 按日志类型拆分采集：
   - `app.log`
   - `audit.log`（或按 `AUDIT` 过滤）
   - `mqtask.log`

## 步骤 3：配置 Filebeat（示例）

```yaml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/demo/app.log
    fields:
      service: demo-service
      env: test
      logType: app
    fields_under_root: true

  - type: log
    enabled: true
    paths:
      - /var/log/demo/audit.log
    fields:
      service: demo-service
      env: test
      logType: audit
    fields_under_root: true

output.elasticsearch:
  hosts: ["http://es:9200"]
  index: "demo-%{[logType]}-%{[env]}-%{+yyyy.MM.dd}"
```

## 步骤 4：配置索引模板与字段映射

1. 先创建模板，固定核心字段类型：
   - `traceId/eventId/auditId/action/status` 用 `keyword`
   - `message/detail/error` 用 `text`
   - `@timestamp` 用 `date`
2. 没有模板就先入库，后续字段容易“漂移”导致查询困难。

## 步骤 5：配置保留策略（ILM）

1. 创建 30/90/180 天三档策略。
2. 绑定到不同索引前缀：
   - `demo-app-*` -> 30d
   - `demo-mqtask-*` -> 90d
   - `demo-audit-*` / `demo-security-*` -> 180d

## 步骤 6：在 Kibana 建 Data View

1. 创建 `demo-app-*`
2. 创建 `demo-audit-*`
3. 创建 `demo-mqtask-*`
4. 创建 `demo-security-*`

## 步骤 7：验证（必须通过）

1. 按 `eventId` 能查到 Outbox 到消费链路。
2. 按 `auditId` 能查到一条审计动作完整记录。
3. 按 `bizId` 能串起同一订单关键日志。
4. 查询 `status=FAIL` 能聚合出前 10 失败动作。

## 步骤 8：灰度与回滚

1. 灰度：先接 10% 测试服务实例，观察 24h。
2. 无异常再全量。
3. 回滚：停止 Filebeat 输出即可，业务服务不需要停机。

---

## 10. Kibana 常用查询（给新手直接复制）

1. 查某事件：
```text
eventId: "8af946fd-618f-4c51-9704-a37a5b567b42"
```

2. 查某订单：
```text
bizId: "900045"
```

3. 查审计失败：
```text
logType: "audit" AND status: "FAILED"
```

4. 查 Outbox 告警关键词：
```text
message: "Outbox 告警"
```

---

## 11. 不影响运行路径的硬约束

1. 日志采集链路与业务链路解耦（异步旁路）。
2. 禁止在业务事务中同步调用 ES。
3. 采集端异常不能抛回业务服务。
4. 生产接入先灰度，不做“一步到位全量切换”。

---

## 12. 实施验收清单（DoD 对齐）

1. 接入团队可按本指南完成测试环境接入。
2. 字段标准、索引命名、保留策略、脱敏规则已全部落地。
3. 能通过 `traceId/eventId/bizId/status` 完成检索。
4. 接入过程中业务路径无新增阻塞或事务行为变化。

---

## 13. 本阶段不做（防误解）

1. Day18 不做 ELK 生产部署。
2. Day18 不强制改造全部历史日志格式。
3. Day18 不引入日志接入导致的业务代码重构。

---

（文件结束）
