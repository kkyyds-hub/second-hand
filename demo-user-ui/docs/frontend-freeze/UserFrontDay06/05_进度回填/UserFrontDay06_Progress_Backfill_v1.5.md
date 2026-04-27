# UserFrontDay06 进度回填

- 日期：`2026-04-24`
- 文档版本：`v1.5`
- 更新类型：`runtime blocker 固化与重跑前置条件收敛`

---

## 1. 本版总结

- `Package-1 seller orders / logistics / ship`：代码、build、runtime 均已回填，继续保持 `pass`。
- `Package-2 order messages`：代码已落地，既有 build 已通过；本轮 runtime 被环境阻断，未观察到订单消息业务请求。
- `Package-3 seller decision`：代码已落地，既有 build 已通过；本轮 runtime 被环境阻断，未 seed 成功 `APPLIED afterSaleId`，未观察到 seller decision 提交请求。
- `Day06`：当前只能写“部分运行态已验证”；不能写 final acceptance，不能推进 Day07。

---

## 2. 三层状态

| 层 | 当前状态 | 说明 |
|---|---|---|
| 代码已落地 | 是 | Package-1 / Package-2 / Package-3 对应前端代码已在当前工作树落地 |
| 构建已通过 | 是 | Package-2 / Package-3 沿用既有 build pass；本轮 fresh build 因 Node `spawn EPERM` 属环境阻断，不能推翻既有 build 结论 |
| 运行态已验证 | 部分是 | Package-1 是；Package-2 / Package-3 否，owner=`environment`，reason=`runtime environment blocked before business request` |

---

## 3. 包级覆盖状态

| 包 | 当前状态 | 证据 | 后续动作 |
|---|---|---|---|
| Package-1 `seller orders / logistics / ship` | 已 runtime pass | `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/userfront-day06-package1-runtime-verify-result.json` | 保持既有 pass，不回退、不外推 |
| Package-2 `order messages` | 已代码+构建；runtime blocked | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-package2-package3-runtime-verify/userfront-day06-package2-package3-runtime-verify-result.json`、`build.log`、`node-child-process-check.log`、`backend-boot-attempt.log` | 环境恢复后重跑 list / send / mark-as-read |
| Package-3 `seller decision` | 已代码+构建；runtime blocked | 同上；另因后端不可用，未能创建有效 `APPLIED afterSaleId` | 环境恢复后先 seed fresh completed order + APPLIED after-sale，再重跑 seller decision |
| Day06 final acceptance | 未完成 | 仍缺 Package-2 / Package-3 runtime pass/fail 真实业务证据 | 等两个 blocked 包重跑后再裁定 |

---

## 4. blocker 固化

| blocker | owner | 固化结论 |
|---|---|---|
| Node `child_process` / Vite-esbuild `spawn EPERM` | environment | 阻断 fresh build、dev server 与浏览器自动化启动前提；本轮不把 fresh build 失败写成代码失败 |
| Java Selector / loopback failure | environment | 阻断 embedded Tomcat 启动，导致 `http://localhost:8080` 不可用 |
| backend unavailable before business API | environment | Package-2 / Package-3 均未进入真实业务请求阶段 |
| missing `APPLIED afterSaleId` seed | environment / precondition | 由后端不可用导致，尚未证明 seller decision 页面或接口契约缺陷 |

---

## 5. 重跑准入清单

后续 verify 线程恢复环境后，至少满足下列条件再重跑：

1. Node spawn preflight 通过，不再出现 `spawnSync cmd.exe EPERM`。
2. Java loopback preflight 通过，不再出现 `Unable to establish loopback connection`。
3. 后端 `http://localhost:8080` 可访问。
4. 前端 runtime 端口可访问。
5. 买家 / 卖家登录态可用。
6. Package-2 使用真实订单进入会话，留存 `GET / POST / PUT read` network JSON 与截图。
7. Package-3 通过业务 API seed fresh completed order + `APPLIED afterSaleId`，再留存 `PUT seller-decision` network JSON 与截图。

---

## 6. 覆盖矩阵口径

- `卖家订单履约` 行保持 `已完成并回填 / 运行态已确认`，只代表 Package-1。
- `订单会话` 行保持 `已代码+构建回填；runtime blocked`，不得写成 pass。
- `卖家售后处理` 行保持 `已代码+构建回填；runtime blocked`，不得写成 pass。
- root README 当前执行日保持 `UserFrontDay06`，不得推进 `UserFrontDay07`。
