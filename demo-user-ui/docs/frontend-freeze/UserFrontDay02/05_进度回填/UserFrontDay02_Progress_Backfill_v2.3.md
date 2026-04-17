# UserFrontDay02 进度回填

- 日期：`2026-04-17`
- 文档版本：`v2.3`
- 当前状态：`进行中（新增：头像上传最小真实闭环 runtime pass；Day02 全量未完成）`

---

## 1. 本轮目标与范围

本轮只处理一个完整工作包：`/account/avatar` 上传链路从 fail 收敛到最小真实闭环。

执行边界：

1. 先定位断裂点（`upload-config -> PUT upload -> PATCH profile`）；
2. 只在最小正确层修复，不改与头像无关子流；
3. 完成 build + dev + route + request + write action 分层复核；
4. 保留 Day02 `进行中`，不把整天升级为已完成。

---

## 2. 断裂点定位（本轮前）

基于 `v2.2` 证据与代码交叉核查，断裂点定位为：

- **层级**：`backend`
- **owner/reason（历史）**：`backend / contract-gap`
- **精确原因**：头像上传端点 CORS 只放行 `5178`，未覆盖本轮 real mode `http://127.0.0.1:5180`，导致浏览器对
  `PUT http://localhost:8080/user/me/avatar/upload?...` 的 preflight 被拦截，出现 `net::ERR_FAILED`，后续 `PATCH /api/user/me/profile` 无法触发。

---

## 3. 最小修复动作

- 后端改动文件：`demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java`
- 修复内容：头像上传专用 CORS 从固定端口放行改为本地多端口模式：
  - `http://user-ui:*`
  - `http://localhost:*`
  - `http://127.0.0.1:*`
- 约束说明：仅作用于 `/user/me/avatar/upload`（及兼容 `/api/user/me/avatar/upload`）映射，不扩散到其他业务接口。

---

## 4. 验证结果（分层）

| 层级 | 结果 | 证据 |
|---|---|---|
| build | pass | `demo-user-ui` 执行 `npm.cmd run build` 成功（产物包含 `AccountAvatarUploadPage-*.js`） |
| dev | pass | 后端 `demo-server-1.0-SNAPSHOT.jar` 监听 `8080`；前端 `npm.cmd run dev:real -- --port 5180` 可访问 `/login` |
| route | pass | 真实登录后可达 `/account/avatar`，页面标题 `Upload Avatar` 正常 |
| request | pass | `POST /api/user/me/upload-config` -> `200/code=1`；`PUT /user/me/avatar/upload` -> `200` 且响应头含 `access-control-allow-origin: http://127.0.0.1:5180`；`PATCH /api/user/me/profile` -> `200/code=1` |
| write action | pass | 页面出现 `Avatar updated.`；`localStorage.user_profile.avatar` 与预览头像地址均完成写回 |

---

## 5. 本轮证据目录

- 根目录：`demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/`
- 关键证据：
  - `avatar-upload/userfront-day02-avatar-minimal-runtime.json`
  - `avatar-upload/network/requests.json`
  - `avatar-upload/network/responses.json`
  - `avatar-upload/network/failed-requests.json`（为空数组）
  - `avatar-upload/screenshots/pre-upload-page.png`
  - `avatar-upload/screenshots/post-upload-page.png`
  - `backend.stdout.log`
  - `frontend.stdout.log`

---

## 6. Day02 当前口径

1. 账户资料补强中的头像上传子流可从 `fail` 升级为独立 runtime `pass`；
2. Day02 仍保持 `进行中`，不得直接写成 `已完成并回填`；
3. 仍需保留边界声明：地址 `edit-only / set-default / delete-only` 当前证据为浏览器可控 mock 运行态，不等于整站联调通过。
