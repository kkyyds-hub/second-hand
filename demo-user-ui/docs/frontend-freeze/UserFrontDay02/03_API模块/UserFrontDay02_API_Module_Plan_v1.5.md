# UserFrontDay02 API 模块规划

- 日期：`2026-03-30`
- 文档版本：`v1.5`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片均已运行回填；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，当前已形成两个“已运行验证”的最小切片：

1. `AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`
2. `地址管理只读起步切片（/account/addresses + GET /user/addresses + loading/empty/error/retry）`

本版只回填运行态结论，不扩写操作域（新增/编辑/删除/设默认）。

---

## 2. 已落地 API 结论（2026-03-30）

| 模块 / 文件 | 当前结论 | 关键证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | `updateMyProfile` 最小链路与同切片 focused regression 继续稳定。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`、`.../userfront-day02-account-profile-focused-regression.json` |
| `demo-user-ui/src/api/address.ts` | `getMyAddressList`（`GET /user/addresses`）只读分页读取、字段归一化、空态兜底已在浏览器链路验证。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/network/goal3-real-get-addresses-request.json`、`.../goal3-real-get-addresses-response.json`、`.../userfront-day02-address-readonly-minimal-runtime.json` |
| `demo-user-ui/src/pages/AddressListPage.vue` | `loading / empty / error / retry` 交互链路可运行验证并留证；真实链路为 empty，mock retry 验证了 list 展示。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/screenshots/goal4-loading-state.png`、`.../goal4-empty-state.png`、`.../goal4-error-state.png`、`.../goal4-retry-after-state.png` |
| `demo-user-ui/src/router/index.ts` + `src/utils/request.ts` | `/account/addresses` 受 `requiresAuth` 保护，未登录拦截至 `/login?redirect=/account/addresses` 口径可复现。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/screenshots/goal1-unauth-redirect-login.png`、`.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal1_requiresAuth_guard.status=pass`） |

---

## 3. 地址只读起步切片（本轮新增）

| 验证目标 | 2026-03-30 结论 | 证据 |
|---|---|---|
| `/account/addresses` requiresAuth 守卫 | pass | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal1_requiresAuth_guard.status=pass`） |
| 登录后到达地址页并触发 `GET /user/addresses` | pass | `.../network/login-password-response.json`、`.../network/goal3-real-get-addresses-request.json`、`.../network/goal3-real-get-addresses-response.json` |
| loading / empty / error / retry | pass | `.../screenshots/goal4-loading-state.png`、`.../goal4-empty-state.png`、`.../goal4-error-state.png`、`.../goal4-retry-after-state.png` |
| list 或 empty 展示 | pass（真实链路 empty；mock retry 验证 list） | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal5_list_or_empty_render.status=pass`） |
| 地址域无写操作 | pass | `.../network/goal6-address-write-ops-observation.json`（`addressWriteRequests=[]`） |

---

## 4. 仍保持计划态的 API 子流

| 模块 / 子流 | 当前状态 | 说明 |
|---|---|---|
| 地址新增 `POST /user/addresses` | 计划中 | 不在本轮只读验证范围。 |
| 地址编辑 `PUT /user/addresses/{id}` | 计划中 | 不在本轮只读验证范围。 |
| 地址删除 `DELETE /user/addresses/{id}` | 计划中 | 不在本轮只读验证范围。 |
| 设置默认地址 `PUT /user/addresses/{id}/default` | 计划中 | 不在本轮只读验证范围。 |
| 头像上传两步链路（`upload-config -> avatar/upload`） | 计划中 | 仍未进入本轮。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 仍未进入本轮。 |

---

## 5. 边界声明

1. 本文档新增的是“地址只读起步切片”运行态回填，不代表 Day02 全量完成；
2. 不得写成“Day02 已完成并回填”或“整站联调已通过”；
3. 写操作链路（新增/编辑/删除/默认地址）仍需后续独立切片验证与回填。

