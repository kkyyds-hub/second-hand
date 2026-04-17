# UserFrontDay02 进度回填

- 日期：`2026-04-17`
- 文档版本：`v2.2`
- 当前状态：`进行中（新增：邮箱绑定/解绑最小运行态 pass；新增：头像上传独立 runtime fail；Day02 全量未完成）`

---

## 1. 本次新增回填结论（双子流）

本次在同一轮 real mode 环境中回填 Day02 剩余两条子流运行态真相：

1. `账号安全与绑定 -> 邮箱绑定/解绑（/account/security/email）`
2. `账户资料补强 -> 头像上传（/account/avatar）`

本轮事实：

- 代码未变更：仅执行 runtime verify 与 docs backfill
- 构建已通过：`demo-user-ui` 执行 `npm.cmd run build` 成功
- 本地 dev 已通过：`vite --mode real --host 0.0.0.0 --port 5180`
- 后端实例：`demo-service` `com.demo.DemoApplication` 监听 `8080`
- 进程清理：本轮临时 `5180/8080` 进程已清理；临时测试图片已清理

---

## 2. 子流级结果（按 verify 口径）

| 子流 | scope | observed behavior | evidence level | 结果 | owner | reason |
|---|---|---|---|---|---|---|
| 邮箱绑定/解绑 | route 可达 + bind/unbind 写路径最小闭环 | `POST /api/user/me/bindings/email` 成功/失败分支均命中；`DELETE /api/user/me/bindings/email` 成功/失败分支均命中；`localStorage.user_profile.email` 成功绑定后写入、成功解绑后清空 | 构建已通过 + dev 已启动 + 浏览器最小链路 + controlled action | `pass` | `n/a` | `n/a` |
| 头像上传 | route 可达 + 文件选择/预览 + upload-config + upload + profile 写回 | `POST /api/user/me/upload-config` 成功（`code=1`）；`PUT /user/me/avatar/upload` 请求 `net::ERR_FAILED`；未出现 `PATCH /api/user/me/profile`；页面报 `Network Error`，session avatar 未更新 | 构建已通过 + dev 已启动 + 浏览器最小链路 + write action 已尝试 | `fail` | `backend` | `contract-gap` |

---

## 3. 证据路径（本轮）

- 运行根目录：`demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-email-avatar-runtime-v3/`
- 邮箱：
  - `email-binding/email-minimal-chain-result.json`
  - `email-binding/script.stdout.log`
- 头像：
  - `avatar-upload/userfront-day02-avatar-minimal-runtime.json`
  - `avatar-upload/network/requests.json`
  - `avatar-upload/network/responses.json`
  - `avatar-upload/network/failed-requests.json`
  - `avatar-upload/screenshots/pre-upload-page.png`
  - `avatar-upload/screenshots/post-upload-page.png`

---

## 4. Day02 当前口径收口

1. Day02 可保留为 `进行中`，不得升级为 `已完成并回填`。
2. `邮箱绑定/解绑` 可升级为：`代码已确认 + 构建已通过 + 运行态已确认（pass）`。
3. `头像上传` 应升级为：`代码已确认 + 构建已通过 + 独立 runtime 已失败（fail, backend/contract-gap）`，不可继续写成“仅待验证”。
4. 头像上传失败已触发跨层联动问题，需由 `drive-demo-user-ui-delivery` 线程最小接棒排查；本线程不改代码。
