# FrontDay10 联调准备与验收

- 日期：`2026-03-16`
- 文档版本：`v1.0`
- 当前状态：`进行中（冻结演示版验收与证据口径）`

---

## 1. Day10 验收目标

Day10 的验收目标不是再做一轮新的“大面积联调清零”，而是把已闭环的运行证据和演示链路整理成**可复述、可查证、可移交**的冻结包。

---

## 2. 环境前置条件

1. 后端服务默认以 `http://localhost:8080` 为准，前端默认以 `http://localhost:5173` 为准。
2. 当前演示主链依赖的管理员账号、token 与基础数据仍需可用。
3. RabbitMQ `localhost:5672` 当前不是 Day10 演示主链必需条件；仅当要验证真实消息投递时才需要补起。
4. Day10 若没有新增运行验证，本文件的验收重点就是**证据入口完整性**，而不是新增一批 pass 结论。

---

## 3. 建议演示 / 走查顺序

| 顺序 | 页面 / 动作 | Day10 验收点 | 证据入口 |
|---|---|---|---|
| 1 | 登录 -> Dashboard | 入口可说明，Dashboard 概览、sellerName、趋势 / 扩展统计证据路径明确 | `FrontDay01/05_进度回填`、`FrontDay09_dashboard_sellername_2026-03-16.json`、`FrontDay09_dashboard_trend_stats_2026-03-16.json` |
| 2 | UserList 封禁 / 解封 | 说明主链动作已闭环，Day10 只做移交口径整理 | `FrontDay09_userlist_ban_unban_2026-03-15.json` |
| 3 | ProductReview 审核通过 / 驳回 | 说明审核主动作已闭环，举报关联处理仍保留边界说明 | `FrontDay09_productreview_approve_reject_2026-03-16.json` |
| 4 | AuditCenter 举报 dismiss / 仲裁 approve | 说明两条主动作已联调通过，保留 `ticketNo` 风险提示 | `FrontDay09_auditcenter_report_dismiss_2026-03-16.json`、`FrontDay09_auditcenter_dispute_arbitrate_2026-03-16.json` |
| 5 | OpsCenter publish-once / refund run-once | 说明真实页面动作已闭环，MQ 投递不是当前必过项 | `FrontDay09_opscenter_write_actions_2026-03-16.json` |
| 6 | SystemSettings 边界 | 说明这是占位页与边界说明，不纳入 Day10 演示主链完成口径 | `FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` |

---

## 4. Day10 需要保留的证据

1. **根入口证据**：`demo-admin-ui/docs/frontend-freeze/README.md`
2. **覆盖总账证据**：`demo-admin-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`
3. **接棒依据证据**：`demo-admin-ui/docs/frontend-freeze/FrontDay09/05_进度回填/FrontDay09_Progress_Backfill_v1.0.md`
4. **Day10 自身文档证据**：`FrontDay10/README.md` 与 `01~05` 全部模块
5. **继承运行态证据**：`FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-15` 与 `2026-03-16` 下的 JSON / PNG

---

## 5. Day10 验收判定

### 5.1 判定为通过的条件

1. 新接手的人能在 5 分钟内定位到：当前执行日、演示主链范围、主要证据入口、遗留问题分层。
2. 每个纳入演示主链的页面 / 动作都至少有一条明确证据路径。
3. 不纳入范围的事项（如 `SystemSettings`、Dashboard 静态趋势装饰、MQ 真实投递）都写得清楚。
4. 根 README、覆盖矩阵、FrontDay10 文档之间没有互相矛盾的状态描述。

### 5.2 不要求在 Day10 当前轮次完成的事项

1. 后端历史测试源码编译问题的修复。
2. 新增功能开发。
3. 新一轮大面积运行态回归。
4. `SystemSettings` 真实接口接入。

### 5.3 失败 / 阻塞时怎么记

- 如果是**文档口径不一致**：记为 Day10 文档治理未完成。
- 如果是**需要改代码 / 改接口 / 重新联调**：先在 `05_进度回填` 记明，再切到 `drive-demo-admin-ui-delivery`。
- 如果是**环境外部问题**（如 MQ、后端历史测试源码编译）：继续保留为边界阻塞，不要虚报成 Day10 前端主链未完成。
