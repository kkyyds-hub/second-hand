# UserFrontDay02 联调准备与验收

- 日期：`2026-04-17`
- 文档版本：`v2.2`
- 当前状态：`进行中（新增：邮箱绑定/解绑最小运行态 pass；新增：头像上传最小运行态 fail=backend/contract-gap）`

---

## 1. 本轮验收范围（最小）

本轮仅覆盖 Day02 剩余两条待独立 runtime 结论子流：

1. `账号安全与绑定 -> 邮箱绑定/解绑`（`/account/security/email`）
2. `账户资料补强 -> 头像上传`（`/account/avatar`）

执行边界：

- 验证顺序：`build -> dev(real) -> browser minimal chain -> controlled write action`
- 不改前端/后端代码，不改 request/router，不做 delivery 修复
- 邮箱子流采用本地受控验证码注入（Redis）+ 新建临时账号验证，避免依赖外部真实邮箱发送
- 头像子流使用最小 PNG 测试素材，仅验证上传链路，不扩展到 Day02 全量验收

---

## 2. Build / Dev / 实例真相

| 验证项 | 结果 | 证据 |
|---|---|---|
| 前端构建 | pass | `demo-user-ui` 执行 `npm.cmd run build` 成功，产物包含 `AccountEmailBindingPage-*.js`、`AccountAvatarUploadPage-*.js` |
| 本地 real mode dev 监听 | pass | `vite --mode real --host 0.0.0.0 --port 5180` 可访问 `http://127.0.0.1:5180/login` |
| 后端实例 | pass | `demo-service` 以 `com.demo.DemoApplication` 监听 `8080` |
| 验证后清理 | pass | 本轮结束后 `5180/8080` 临时进程已清理；临时头像测试图片已清理 |

---

## 3. 子流结论（独立）

| 子流 | scope | observed behavior | evidence level | 结论 | owner | reason |
|---|---|---|---|---|---|---|
| 邮箱绑定/解绑（`/account/security/email`） | route reachability + bind/unbind 最小写路径 | route 可达；`POST /api/user/me/bindings/email` 成功分支 `code=1` 与失败分支 `code=0` 均观察到；`DELETE /api/user/me/bindings/email` 失败/成功分支均观察到；`localStorage.user_profile.email` 在 bind success 后更新、unbind success 后清空 | 构建已通过 + dev 已启动 + 浏览器最小链路 + controlled action 已确认 | `pass` | `n/a` | `n/a` |
| 头像上传（`/account/avatar`） | route reachability + 文件选择/预览 + upload-config + upload + profile write-back | route 可达；文件选择与预览可见；`POST /api/user/me/upload-config` 返回 `code=1`；随后 `PUT http://localhost:8080/user/me/avatar/upload...` 请求 `net::ERR_FAILED`；未观察到后续 `PATCH /api/user/me/profile`；页面提示 `Network Error`，session avatar 未写回 | 构建已通过 + dev 已启动 + 浏览器最小链路 + write action 已尝试 | `fail` | `backend` | `contract-gap` |

---

## 4. 本轮证据

- 运行根目录：`demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-email-avatar-runtime-v3/`
- 邮箱子流主证据：
  - `email-binding/email-minimal-chain-result.json`
  - `email-binding/script.stdout.log`
- 头像子流主证据：
  - `avatar-upload/userfront-day02-avatar-minimal-runtime.json`
  - `avatar-upload/network/requests.json`
  - `avatar-upload/network/responses.json`
  - `avatar-upload/network/failed-requests.json`
  - `avatar-upload/screenshots/pre-upload-page.png`
  - `avatar-upload/screenshots/post-upload-page.png`

---

## 5. 结论口径

1. 可升级声明：`邮箱绑定/解绑子流已取得独立最小运行态 pass`。
2. 必须保留声明：`头像上传子流已有独立 runtime 失败结论（fail, owner=backend, reason=contract-gap）`。
3. 本轮不可写成：`Day02 已完成并回填`、`账户中心全量联调通过`、`整站联调已通过`。
4. 因头像上传暴露跨层联动问题（upload-url/跨域或上传契约链路），后续应由 `drive-demo-user-ui-delivery` 线程接棒处理；本线程仅保留 verify 结论。
