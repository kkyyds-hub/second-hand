# Day17 P5-S3 问题处置清单 v1.0

- 日期：2026-02-24
- 目标：对高风险告警给出“问题-影响-处置-结论”的闭环记录。

---

## 1. 问题总览

| ID | 类别 | 级别 | 现象摘要 | 处置结论 | 状态 |
|---|---|---|---|---|---|
| QG-001 | 标准输出 | 高 | 存在 `printStackTrace/System.out`（19处） | 不在 Day17 关键链路，纳入后续治理 | 已记录 |
| QG-002 | 异常边界 | 高 | 存在 `catch(Exception)`（48处） | Day17 链路保留日志与分流，不作为阻断 | 已记录 |
| QG-003 | 可维护性 | 中 | `TODO` 注释（2处） | 定位于物流解析扩展点，业务不阻断 | 已记录 |
| QG-004 | 扫描工具 | 中 | 本机无法直接跑 Sonar Maven 扫描 | 提供 Sonar 接入命令，改在 CI/IDE 执行 | 已记录 |
| QG-005 | SQL 性能余项 | 中 | Outbox 计划中仍可能显示 `Using filesort` | 已完成“排序集合收口”，后续继续优化 | 已记录 |

---

## 2. 明细处置

### QG-001：标准输出与 `printStackTrace`

- 证据：`demo-common/src/main/java/com/demo/utils/HttpClientUtil.java`、`AliOssUtil.java`、`WeChatPayUtil.java`，`demo-service/src/main/java/com/demo/websocket/WebSocketServer.java` 等。
- 风险：日志不可控、线上排障可观测性不足。
- 本次处置：
  1. Day17 核心链路（service/job/mapper）未新增该模式；
  2. 在本次门禁中列为历史技术债，不阻断 Day17 交付。
- 后续建议：统一替换为 `Slf4j` 结构化日志，并按模块分批收敛。

### QG-002：通用异常捕获 `catch (Exception)`

- 证据：扫描结果 `48` 处，涉及 controller/service/job/consumer。
- 风险：异常语义被泛化，可能隐藏边界错误。
- 本次处置：
  1. Day17 关键链路（Outbox、任务处理、消费端）保留“记录日志 + 明确分流（ACK/NACK 或幂等分流）”；
  2. 对并发/幂等场景已补充回归用例，防止吞异常导致状态错乱。
- 结论：当前实现可接受，但需在后续版本逐步收敛为“业务异常 + 系统异常”分层捕获。

### QG-003：TODO 注释

- 证据：`demo-service/src/main/java/com/demo/logistics/DeliveryTrackerProvider.java`（2处）。
- 风险：功能完整性依赖后续补齐。
- 本次处置：判定为“可控技术债”，不影响 Day17 的 DAO/事务/幂等等核心目标。
- 后续建议：补充物流响应 JSON 到 `LogisticsTrackResult.trace` 的映射实现。

### QG-004：Sonar 扫描未在本机执行

- 证据：
  1. `cmd /c mvn -v` 不可用；
  2. `wsl mvn -v` 受限（`E_ACCESSDENIED`）。
- 风险：无法在当前终端直接产出 Sonar Quality Gate 报告。
- 本次处置：
  1. 保留可执行 Sonar 命令与参数规范；
  2. 采用等价门禁（回归+静态模式扫描+SQL证据）先完成 Day17 交付。
- 后续动作：在 CI/IDE 环境跑 Sonar 并回填报告链接或截图。

### QG-005：Outbox filesort 余项

- 证据：`day17回归/慢SQL与索引治理/Day17_P3_S4_Outbox_filesort收口_v1.0.md`
- 风险：积压量大时排序开销增长。
- 本次处置：
  1. 采用“分支限流 + 合并排序”改写，保证语义不变；
  2. 将排序集合上界收口到 `2 * limit`。
- 结论：当前可交付，后续继续评估 `next_retry_time` 条件拆分等优化。

---

## 3. 阻断项判定

1. Day17 交付范围内：**无阻断级遗留问题**。  
2. 历史技术债：已登记、已给出后续治理方向，不阻断本次里程碑验收。

---

（文件结束）
