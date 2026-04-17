# UserFrontDay02 联调准备与验收

- 日期：`2026-04-17`
- 文档版本：`v2.3`
- 当前状态：`进行中（头像上传子流已从 fail 升级为最小真实闭环 pass）`

---

## 1. 本轮联调目标（最小）

仅复核一个子流：`账户资料补强 -> 头像上传（/account/avatar）`

验证顺序：

`build -> dev(real) -> route -> request -> controlled write action`

---

## 2. 预期与通过条件

通过条件如下，需全部满足：

1. 页面可达，文件选择/预览可用；
2. `POST /api/user/me/upload-config` 正常；
3. `PUT /user/me/avatar/upload` 不再出现 `net::ERR_FAILED`；
4. `PATCH /api/user/me/profile` 被触发且成功；
5. session / profile 头像写回有可见证据。

---

## 3. 实际结果（本轮）

| 验证层 | 结果 | 观测 |
|---|---|---|
| build | pass | `npm.cmd run build` 成功 |
| dev | pass | 后端 `8080` 与前端 `5180` 均可访问 |
| route | pass | 登录后进入 `/account/avatar` 成功 |
| request | pass | `upload-config` `200/code=1`；`PUT avatar/upload` `200`；`PATCH profile` `200/code=1` |
| write action | pass | 页面提示 `Avatar updated.`；`localStorage.user_profile.avatar` 更新为新资源 URL |

---

## 4. 断裂点归因与修复层

- 历史失败点：`PUT /user/me/avatar/upload` preflight CORS 拦截；
- 最终归因：`backend`；
- 修复层：`demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java` 头像上传端点 CORS 放行范围调整；
- 结论：`/account/avatar` 最小真实闭环已通过，原 `backend/contract-gap` 结论在该子流上可关闭。

---

## 5. 证据路径

- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/userfront-day02-avatar-minimal-runtime.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/network/requests.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/network/responses.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/network/failed-requests.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/screenshots/pre-upload-page.png`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/screenshots/post-upload-page.png`

---

## 6. 边界声明

1. 本轮不等于 Day02 全量验收通过；
2. Day02 总状态继续维持 `进行中`；
3. 本轮未改动邮箱绑定/解绑与地址子流结论。
