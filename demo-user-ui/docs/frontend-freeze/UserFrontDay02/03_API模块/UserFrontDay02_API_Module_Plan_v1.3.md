# UserFrontDay02 API 模块规划

- 日期：`2026-03-26`
- 文档版本：`v1.3`
- 当前状态：`进行中（首个最小切片已运行通过；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，先把“AccountCenter 昵称/简介编辑 + `PATCH /user/me/profile` + `saveCurrentUser()` 回写”从规划态升级为已验证最小链路；其余子流仍保持计划态。

---

## 2. 本轮最小切片 API 落地结论（2026-03-26）

| 模块 / 文件 | 当前结论 | 证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | 已存在并承接 Day02 第一刀；对外提供 `updateMyProfile`，调用 `PATCH /user/me/profile`，并在 API 层做返回体兼容合并。 | `demo-user-ui/src/api/profile.ts`、`demo-user-ui/.tmp_runtime/2026-03-26-userfront-day02-account-profile-edit-minimal/network/profile-patch-request.json`、`.../profile-patch-response.json` |
| `demo-user-ui/src/pages/AccountCenterPage.vue` | 已接入资料编辑表单（昵称/简介）、提交态控制、成功提示，并在成功后调用 `saveCurrentUser()` 回写会话快照。 | `demo-user-ui/src/pages/AccountCenterPage.vue`、`demo-user-ui/.tmp_runtime/2026-03-26-userfront-day02-account-profile-edit-minimal/screenshots/account-saving-state.png`、`.../account-save-success.png`、`.../account-after-refresh.png` |
| `demo-user-ui/src/utils/request.ts` | Day01 基线继续复用：`authentication` 请求头与会话读写 helper 可被 Day02 最小切片直接消费。 | `demo-user-ui/src/utils/request.ts`、`demo-user-ui/.tmp_runtime/2026-03-26-userfront-day02-account-profile-edit-minimal/network/profile-patch-request.json` |

---

## 3. 仍保持计划态的 API 子流

| 模块 / 文件 | 当前状态 | 说明 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` 的头像上传相关能力 | 计划中 | `upload-config -> avatar/upload` 两步链路尚未进入本轮验证。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 不在本轮最小链路范围内。 |
| `demo-user-ui/src/api/address.ts` 与地址页面消费 | 计划中 | 地址管理尚未进入本轮实现与验证。 |

---

## 4. 边界声明

1. 本文档仅把 Day02 首个最小切片升级为“代码 + 构建 + 运行态已确认”；
2. 这不代表 Day02 全部完成，更不代表整站联调已通过；
3. Day02 后续子流仍按 `UserFrontDay02/05_进度回填` 的增量证据继续升级。
