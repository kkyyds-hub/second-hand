# UserFrontDay06 联调准备与验收

- 日期：`2026-04-24`
- 文档版本：`v1.3`
- 更新类型：`runtime blocker 固化与重跑前置条件收敛`
- 当前状态：`进行中（Package-1 已 runtime pass；Package-2 / Package-3 已代码+既有 build，但运行态被环境阻断；Day06 仍未收口）`

---

## 1. 本版固化的三层事实

| 范围 | 代码已落地 | 构建已通过 | 运行态已验证 | 口径 |
|---|---|---|---|---|
| Package-1 `seller orders / logistics / ship` | 是 | 是 | 是 | 沿用 `2026-04-22` runtime pass 证据 |
| Package-2 `order messages` | 是 | 是（沿用既有 build） | 否 | `2026-04-24` runtime attempt 在业务请求前被环境阻断 |
| Package-3 `seller decision` | 是 | 是（沿用既有 build） | 否 | `2026-04-24` runtime attempt 无法 seed `APPLIED afterSaleId`，未进入业务提交 |
| Day06 final acceptance | 部分 | 部分 | 部分 | 不能写成“已完成并回填”，也不能推进 Day07 |

本版只固化 blocker 与重跑入口，不新增业务实现、不补后端、不补 seller after-sale 查询接口、不把 blocked 包改写为 pass。

---

## 2. runtime blocker 目录与证据口径

本轮 blocker 目录：

- `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-package2-package3-runtime-verify/`

| 证据文件 | 观察结论 | 对 runtime 的影响 |
|---|---|---|
| `build.log` | fresh `npm.cmd run build` 在 Vite / esbuild 加载阶段触发 `spawn EPERM` | 本轮 fresh build 不可作为成功证据；只能沿用既有 build pass |
| `node-child-process-check.log` | Node `child_process.spawnSync('cmd.exe')` 返回 `EPERM` | Vite dev / Playwright / 子进程自动化前提不成立 |
| `java-loopback-check.log` | Java `Selector.open()` 报 `Unable to establish loopback connection` | Java 本地 loopback 能力异常，后端启动前提不成立 |
| `backend-boot-attempt.log` | `mvn.cmd -pl :demo-server spring-boot:run` embedded Tomcat 启动失败 | `http://localhost:8080` 不可用，业务 API 不能验证 |
| `userfront-day06-package2-package3-runtime-verify-result.json` | Package-2 / Package-3 均为 `blocked`，owner=`environment` | 结构化阻断结论；不是业务缺陷裁定 |
| `summary.md` | 汇总 blocker、owner、reason、截图/seed 状态 | 后续重跑入口说明 |

固定结论：本轮失败点发生在业务请求之前，不能据此判定前端页面、API 模块或后端 controller 存在业务缺陷。

---

## 3. 恢复环境后的重跑前置条件

后续 verify 线程必须先确认以下前置条件全部满足，再重跑 Package-2 / Package-3：

1. Node 子进程能力恢复：`node child_process` 可正常 spawn，`npm.cmd run build` 至少能进入正常构建流程，不再出现 `spawn EPERM`。
2. Java loopback 能力恢复：Java `Selector.open()` / 本地 loopback 不再报 `Unable to establish loopback connection`。
3. 后端可启动并可访问：`demo-server` boot 成功，`http://localhost:8080` 可用。
4. 前端 runtime 可启动并可访问：`http://localhost:5175` 或本轮指定端口可用。
5. 登录凭据 / session 可用：买家、卖家会话来源明确，不能用未登录状态覆盖业务结论。
6. Package-3 前置数据可创建：必须通过业务 API seed fresh completed order + buyer after-sale apply，拿到有效 `APPLIED afterSaleId`；不得凭猜测 ID 写 pass。
7. 新 runtime 产物目录独立落盘：network JSON、screenshots、summary/result JSON 必须使用新时间戳目录，避免覆盖本轮 blocked 证据。

---

## 4. 环境恢复后必须重跑的链路

| 包 | 重跑链路 | 必须观察到的业务请求 / 证据 |
|---|---|---|
| Package-2 `order messages` | 订单会话 `list / send / mark-as-read` | `GET /api/user/messages/orders/{orderId}`、`POST /api/user/messages/orders/{orderId}`、`PUT /api/user/messages/orders/{orderId}/read`；对应 network JSON 与截图 |
| Package-3 `seller decision` | seller decision 页 URL query 预填 / 手动输入 `afterSaleId` 后同意或拒绝 | `PUT /api/user/after-sales/{afterSaleId}/seller-decision`；有效 `APPLIED afterSaleId` seed 证据、network JSON 与截图 |

若环境恢复后业务请求真实失败，才按 owner / reason 判定前端、后端或数据前置责任；在此之前保持 `runtime environment blocked`。

---

## 5. 与 Package-1 pass 的关系

Package-1 结论不回退：

- 产物：`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/`
- 结论：auth guard / seller list / seller detail / logistics before ship / ship submit / logistics after ship 均 `pass`

但 Package-1 pass 不能外推为 Package-2 / Package-3 pass，也不能外推为 Day06 final acceptance。

---

## 6. 本版不做事项

- 不改 `src/`、不改业务代码、不改后端。
- 不补 seller after-sale 查询接口。
- 不新增无证据截图，不伪造 network 响应。
- 不把 root README 推进到 Day07。
- 不把覆盖矩阵中 Package-2 / Package-3 写成 runtime pass。
