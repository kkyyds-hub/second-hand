# FrontDay07 联调准备与验收

- 日期：`2026-03-15`
- 文档版本：`v1.3`
- 当前状态：`已完成并回填`

---

## 1. 环境前置条件

1. 后端服务可访问，默认以 `http://localhost:8080` 为准。
2. 前端以 `demo-admin-ui` 项目运行，常用命令为 `npm run dev:real`。
3. 当前页面需要的管理员账号与 token 可用。

---

## 2. 当日联调清单

1. `Dashboard / OpsCenter / AuditCenter / SystemSettings / Login` 的页面入口在真实 dev server 下可正常打开。
2. `Dashboard / OpsCenter / AuditCenter` 的当前只读接口在管理员登录态下返回 HTTP `200`，不再出现历史 token `401` 阻塞。
3. `OpsCenter.vue` 的反馈文案不再暴露“只读快照”或 `sent=/success=` 这类实现态提示；`AuditCenter.vue` / `Login.vue` 不再向用户暴露“页面已接真实聚合数据 / 暂未开放”这类过程型文案。
4. `SystemSettings.vue` 继续保持静态范围，不新增 `/admin/settings/*` 请求；Login 找回密码弹窗使用正式业务说明。
5. 本轮只验证登录、页面加载与 GET 查询，不执行 `publish-once`、`run-once` 或审计写入类真实副作用接口。

---

## 3. 当日需要保留的证据

1. 受影响页面与文案收口点。
2. `npm.cmd run build` 结果。
3. 页面入口 / 认证态页面走查结果、只读接口记录与副作用接口边界说明。

---

## 4. 实测结果（2026-03-15）

1. **构建通过**
   - 在 `demo-admin-ui` 目录执行 `npm.cmd run build`，通过。

2. **页面入口可访问**
   - `http://localhost:5173/` -> HTTP `200`
   - `http://localhost:5173/ops-center` -> HTTP `200`
   - `http://localhost:5173/audit` -> HTTP `200`
   - `http://localhost:5173/settings` -> HTTP `200`
   - `http://localhost:5173/login` -> HTTP `200`

3. **本轮代码收口点已落地**
   - `OpsCenter.vue` 已将“只读快照待刷新 / 待补齐”统一改成“运行概览待刷新 / 待补齐”，并把执行结果中的 `sent=/failed=/success=` 改成业务态反馈。
   - `AuditCenter.vue` 已移除“页面已接真实聚合数据”这类实现态提示。
   - `Dashboard.vue` 已完成边角提示、空态与风险标签文案收口。
   - `Login.vue` / `UserList.vue` / `ProductReview.vue` 的核心弹窗说明与空态文案已完成最后一轮业务化收口。

4. **历史登录态阻塞已定位**
   - `2026-03-15` 初次继续复用浏览器中的历史管理员登录态做权限接口校验时返回 `401`。
   - 说明当前本地历史 token 已失效，阻塞点来自过期登录态而非页面实现本身。

5. **重新登录后的认证态窄范围走查已补证**
   - Dashboard：当前管理员重新登录后，在真实 dev server 下实际触发 `/api/admin/dashboard/overview?date=2026-03-15`、`/api/admin/statistics/dau?date=2026-03-15`、`/api/admin/statistics/order-gmv?date=2026-03-15`、`/api/admin/statistics/product-publish?date=2026-03-15` 与 `/api/admin/audit/overview?riskLevel=HIGH`，均返回 HTTP `200`；页面展示 `GMV=¥0 / 订单数=0 / 发布量=0` 的真实结果，并保留“优先跟进事项”展示高优事项兜底。
   - OpsCenter：页面加载时 `/api/admin/ops/outbox/metrics`、`/api/admin/orders?page=1&pageSize=1`、`/api/admin/ops/tasks/ship-timeout?page=1&pageSize=1`、`/api/admin/ops/tasks/refund?page=1&pageSize=1`、`/api/admin/ops/tasks/ship-reminder?page=1&pageSize=1`、`/api/admin/users/user-violations/statistics` 均返回 HTTP `200`；页面实际展示 `订单总量=68 / Outbox 已发送=84 / 发货超时=29 / 退款任务=29 / 发货提醒=54 / Top1=ship_timeout(1795)`，且未再出现“只读快照”文案。
   - AuditCenter：页面加载时 `/api/admin/audit/overview` 返回 HTTP `200`；当前统计为 `pendingDisputes=0 / urgentReports=2 / platformIntervention=0 / todayNewClues=0`，筛选区与工单列表均可正常渲染，页面也不再直接暴露 `sourceId / ticketNo` 字段名。
   - SystemSettings：页面可正常渲染“配置中心使用说明 / 系统状态摘要”等静态内容，本轮未观察到 `/api/admin/settings/*` 请求。
   - Login：未登录态页面可正常打开；点击“忘记密码？”后弹窗展示“找回密码帮助 / 恢复账号访问”等正式业务文案，不再使用“暂未开放”表达。

6. **副作用接口执行边界**
   - 本轮只做登录、页面加载和查询态验证，未执行 `POST /admin/ops/outbox/publish-once`、`POST /admin/ops/tasks/*/run-once`，也未执行审计处理类 `PUT` 接口。

7. **Day07 范围完成判定**
   - 书面冻结、关键页面落地、窄范围运行证据与最终文案复查均已完成，满足 Day07 的退出标准。
   - 更大范围视觉回归继续作为后续执行日的扩展验证，不再作为 Day07 的阻塞项。

---

## 5. 验收判定建议

1. 能实际执行的项，优先记录为“运行态已确认”。
2. 只有代码存在但未实测的项，记录为“代码已完成待运行验证”。
3. 联调失败时，必须注明是前端问题、后端问题还是环境阻塞。




