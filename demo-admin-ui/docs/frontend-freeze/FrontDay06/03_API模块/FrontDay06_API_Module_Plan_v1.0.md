# FrontDay06 API 模块方案

- 日期：`2026-03-15`
- 文档版本：`v1.2`
- 当前状态：`已完成并回填`

---

## 1. 本轮改动目标

把 OpsCenter 的运行态汇总逻辑收口到 API 边界，避免页面层分散处理多接口失败与字段兜底，并明确 SystemSettings 不新增伪接口。

---

## 2. API 层方案

### 2.1 `src/api/adminExtra.ts`

新增并固化 `fetchOpsRuntimeBundle()`：

1. 通过 `Promise.allSettled()` 并行读取：
   - `fetchAdminOrders(1, 1)`
   - `fetchOutboxMetrics()`
   - `fetchShipTimeoutTasks(1, 1)`
   - `fetchRefundTasks(1, 1)`
   - `fetchShipReminderTasks(1, 1)`
   - `fetchViolationStatistics()`
2. 统一输出：
   - `snapshot`
   - `availability`
   - `hasAnySuccess`
   - `failedSources`
3. 在 API 层完成字段兜底：
   - 数量型字段统一转 `number`
   - 任务列表只消费 `total`
   - 违规 Top1 为空时回退为 `--`

### 2.2 页面消费方式

`OpsCenter.vue` 不再自己拼 Promise 与逐个 try / catch，而是统一消费 bundle：

- `runtimeAvailability` 决定卡片显示数字还是 `—`
- `runtimeError` 决定页面展示 warning / danger banner
- `failedSources` 直接映射成“哪些快照未同步”

---

## 3. SystemSettings 处理策略

`SystemSettings.vue` 本轮**不新增 API 模块**：

1. 不补 `/admin/settings/*` 之类的伪接口；
2. 不在页面里强行制造请求层；
3. 页面只保留静态配置概览、入口跳转与说明弹窗。

---

## 4. 收口结论

1. 真实字段兼容逻辑集中留在 `src/api/adminExtra.ts`，不散落在模板层。
2. `OpsCenter.vue` 只负责展示 bundle 结果与状态提示。
3. Day06 的 API 层目标已经达成，因此在只读范围内可以关闭。
4. 后续若要验证写动作，可在现有 API 模块上继续扩展，但不影响 Day06 的闭环判断。
