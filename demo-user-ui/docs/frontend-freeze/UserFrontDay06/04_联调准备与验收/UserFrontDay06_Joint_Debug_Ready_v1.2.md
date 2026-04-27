# UserFrontDay06 联调准备与验收

- 日期：`2026-04-24`
- 文档版本：`v1.2`
- 当前状态：`进行中（Package-1 已 runtime pass；Package-2 order messages 与 Package-3 seller decision 本轮 runtime attempt 被环境阻断，未产出业务请求证据；Day06 仍未收口）`

---

## 1. 本轮验证目标

本轮原计划一次性补齐 Day06 剩余两个 runtime 包：

1. Package-2 `order messages`
   - 目标链路：订单会话 `list / send / mark-as-read`
   - 目标请求：`GET /api/user/messages/orders/{orderId}`、`POST /api/user/messages/orders/{orderId}`、`PUT /api/user/messages/orders/{orderId}/read`
2. Package-3 `seller decision`
   - 目标链路：独立 seller decision 页 `afterSaleId` URL 预填 / 手动输入 + 提交
   - 目标请求：`PUT /api/user/after-sales/{afterSaleId}/seller-decision`

本轮不做实现、不改后端、不补 seller after-sale 查询接口、不把 Day06 写成 final acceptance。

---

## 2. 实际执行结论

| 包 / 链路 | 本轮结论 | 证据状态 | owner | reason |
|---|---|---|---|---|
| Package-2 `order messages` | blocked | 未观察到 `GET/POST/PUT /api/user/messages/orders/...` 业务请求；截图 0 | environment | 后端 boot 在 Tomcat/Java Selector loopback 阶段失败；前端 fresh build/dev/browser automation 也被 Node `child_process` `EPERM` 阻断 |
| Package-3 `seller decision` | blocked | 未观察到 `PUT /api/user/after-sales/{afterSaleId}/seller-decision`；未能 seed 有效 `APPLIED afterSaleId`；截图 0 | environment | 后端不可用，无法用业务 API 创建 completed order / after-sale precondition |
| Day06 final acceptance | blocked | 不具备剩余包 runtime pass 证据 | environment | 不能越过 runtime blocker 写“已完成并回填” |

> 重要口径：本轮没有证明 Package-2 / Package-3 存在产品缺陷；失败点发生在业务请求前的本地运行环境启动 / 自动化能力层。

---

## 3. 环境阻断证据

运行态产物目录：

- `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-package2-package3-runtime-verify/`

关键证据：

- `build.log`
  - fresh `npm.cmd run build` 在 Vite 读取配置阶段触发 `Error: spawn EPERM`。
- `node-child-process-check.log`
  - Node `child_process.spawnSync('cmd.exe')` 返回 `EPERM`，因此 Vite/esbuild 与 Playwright 类自动化均不具备可信启动前提。
- `java-loopback-check.log`
  - Java `Selector.open()` 报 `Unable to establish loopback connection` / `Invalid argument: connect`。
- `backend-boot-attempt.log`
  - `mvn.cmd -pl :demo-server spring-boot:run` 在 embedded Tomcat 启动阶段失败：`Unable to start embedded Tomcat server`，根因同 Java loopback/Selector。
- `userfront-day06-package2-package3-runtime-verify-result.json`
  - 结构化记录 Package-2 / Package-3 均为 `blocked`。
- `summary.md`
  - 本轮摘要与 owner / reason。

---

## 4. 与 Package-1 既有结论的关系

Package-1 `seller orders / logistics / ship` 仍沿用既有 runtime pass 结论：

- 产物：`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/`
- 结果：auth guard / seller list / seller detail / logistics before ship / ship submit / logistics after ship 均 `pass`

本轮 blocked 不回退 Package-1 结论，但也不能把 Package-2 / Package-3 或 Day06 整体补写为 pass。

---

## 5. 后续动作

1. 先恢复本地 runtime 执行环境：
   - Node 进程能够正常 `child_process` spawn；
   - Java 能正常 `Selector.open()` / Tomcat boot；
   - 后端 `http://localhost:8080`、前端 `http://localhost:5175` 可访问。
2. 环境恢复后重跑 Package-2：订单会话 list / send / mark-as-read。
3. 环境恢复后重跑 Package-3：用业务 API seed fresh completed order + APPLIED after-sale，再验证 seller decision 提交。
4. 两包都有真实 runtime 证据后，才能推进 Day06 final acceptance。
