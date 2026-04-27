# UserFrontDay06 进度回填

- 日期：`2026-04-24`
- 文档版本：`v1.4`

---

## 1. 总结

- `Package-1 seller orders / logistics / ship`：已完成代码、build、runtime 回填，结论继续保持 `pass`。
- `Package-2 order messages`：代码与既有 build 回填仍保留；本轮 runtime attempt 被环境阻断，未观察到业务请求，结论为 `blocked`，不是 `pass`。
- `Package-3 seller decision`：代码与既有 build 回填仍保留；本轮 runtime attempt 被环境阻断，未能 seed `APPLIED afterSaleId`，结论为 `blocked`，不是 `pass`。
- `Day06 final acceptance`：仍未完成，不能写成“已完成并回填”。

---

## 2. 包级状态

| 包 | 代码落地 | 构建 | 运行态 | 本轮证据 |
|---|---|---|---|---|
| Package-1 `seller orders / logistics / ship` | 是 | 是 | 是 | `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/userfront-day06-package1-runtime-verify-result.json` |
| Package-2 `order messages` | 是 | 是（沿用既有 build 回填；本轮 fresh build 环境被 `spawn EPERM` 阻断） | blocked | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-package2-package3-runtime-verify/userfront-day06-package2-package3-runtime-verify-result.json`、`build.log`、`node-child-process-check.log`、`backend-boot-attempt.log` |
| Package-3 `seller decision` | 是 | 是（沿用既有 build 回填；本轮 fresh build 环境被 `spawn EPERM` 阻断） | blocked | 同上；未生成有效 seller decision 请求截图 / network 业务响应 |
| Day06 final acceptance | 否 | 否 | 否 | 仍依赖 Package-2 / Package-3 后续 runtime pass 证据 |

---

## 3. 本轮 runtime attempt 事实

运行态产物目录：

- `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-package2-package3-runtime-verify/`

目录结构已按 verify 包落盘：

- `order-messages/`
- `seller-decision/`
- `network/`
- `screenshots/`
- `precondition-seed/`

实际结果：

1. `order-messages`
   - 目标：list / send / mark-as-read。
   - 实际：未进入浏览器业务链路；`GET/POST/PUT /api/user/messages/orders/...` 计数均为 0。
   - 结论：`blocked / environment`。
2. `seller-decision`
   - 目标：`afterSaleId` 预填 / 手动输入后提交 `PUT seller-decision`。
   - 实际：后端不可用，无法先用业务 API seed fresh completed order + APPLIED after-sale；`PUT /api/user/after-sales/{afterSaleId}/seller-decision` 计数为 0。
   - 结论：`blocked / environment`。

---

## 4. blocker / 风险

| 项目 | 结论 | owner | reason |
|---|---|---|---|
| Node child process | blocker | environment | `node-child-process-check.log` 显示 `spawnSync cmd.exe EPERM`；fresh Vite build/dev 与 Playwright 自动化不具备启动前提 |
| Java / Tomcat loopback | blocker | environment | `java-loopback-check.log` 与 `backend-boot-attempt.log` 显示 `Unable to establish loopback connection`，后端 `8080` 未能启动 |
| Package-2 业务链路 | blocked | environment | 业务请求前即被环境阻断，未证明前端/后端契约缺陷 |
| Package-3 业务链路 | blocked | environment | 无法创建/获取有效 `APPLIED afterSaleId` 前置条件，未证明产品缺陷 |

---

## 5. 后续待办

1. 修复本地 runtime 环境后重跑 Package-2 `order messages`。
2. 修复本地 runtime 环境后重跑 Package-3 `seller decision`。
3. 重跑时必须补齐 network JSON 与 screenshots；若业务请求真实失败，再按 owner / reason 判定是否需要实现线程。
4. 只有 Package-2 与 Package-3 均有 runtime pass 证据后，才能写 Day06 final acceptance。
