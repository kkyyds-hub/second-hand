# FrontDay05 API 模块计划

- 日期：`2026-03-14`
- 文档版本：`v1.1`
- 当前状态：`已完成并回填`

---

## 1. 模块目标

把首页真实只读接口、统计接口和本地趋势占位的职责边界固定下来，避免页面层继续混写协议适配与业务结论。

---

## 2. 重点文件

| 文件 | 角色 | Day05 结论 |
|---|---|---|
| `demo-admin-ui/src/api/dashboard.ts` | 首页总览 API | 负责 `overview` 主聚合读取，以及首页高优处理队列的只读兜底策略。 |
| `demo-admin-ui/src/api/adminExtra.ts` | 扩展统计 API | 负责 `dau / order-gmv / product-publish` 的 bundle 聚合与 availability 标记。 |
| `demo-admin-ui/src/pages/Dashboard.vue` | 页面消费层 | 只消费已冻结的接口结果与本地趋势图占位，不在模板层散写协议判断。 |
| `demo-admin-ui/src/utils/request.ts` | 请求边界 | 保持统一返回结构 `{ code, msg, data }` 的拆包逻辑。 |

---

## 3. Day05 固定下来的 API 规则

1. 首页核心真实只读数据由 `overview + statistics` 组成。
2. 统计接口允许局部失败，但页面不能因此丢掉其他已成功返回的数据。
3. 趋势图仍保留本地 `mockTrendData`，不伪装成真实接口能力。
4. 页面层不因为“想赶快看到效果”而把字段适配散落回模板中。

---

## 4. 收口结论

1. 首页真实接口与本地占位的所有权已经清晰：
   - `dashboard.ts` 管总览与首页队列口径；
   - `adminExtra.ts` 管统计 bundle；
   - `Dashboard.vue` 只负责展示与状态表达。
2. Day05 的 API 模块结论已经稳定，可作为后续继续补强 Dashboard 的基础。
3. Day05 本身已经闭环，后续若继续扩展统计或趋势图真实化，应归到新的执行日继续推进。
