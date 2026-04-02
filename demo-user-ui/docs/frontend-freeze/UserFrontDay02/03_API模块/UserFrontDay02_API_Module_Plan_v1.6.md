# UserFrontDay02 API 模块规划

- 日期：`2026-03-30`
- 文档版本：`v1.6`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片 + 地址新增 create-only 切片均已运行回填；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，当前已形成三个“已运行验证”的最小切片：

1. `AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`
2. `地址管理只读起步切片（/account/addresses + GET /user/addresses + loading/empty/error/retry）`
3. `地址新增 create-only 切片（/account/addresses/new + POST /user/addresses + 成功跳转 + 失败不持久化）`

本版为运行态回填，不扩 Day02 写操作全域，不扩 Day03+。

---

## 2. 已落地 API 结论（2026-03-30）

| 模块 / 文件 | 当前结论 | 关键证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | `updateMyProfile` 最小链路与同切片 focused regression 继续稳定。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`、`.../userfront-day02-account-profile-focused-regression.json` |
| `demo-user-ui/src/api/address.ts` | `getMyAddressList`（只读）与 `createMyAddress`（create-only）均已在浏览器链路运行验证通过；`createMyAddress` 成功分支可落库并回列表可见，失败 mock 分支可恢复且不持久化。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/summary.md`、`.../userfront-day02-address-create-only-minimal-runtime.json`、`.../network/goal3-success-post-request-attempt1.json`、`.../network/goal3-success-post-response-attempt1.json`、`.../network/goal6-no-persist-get-response.json` |
| `demo-user-ui/src/pages/AddressListPage.vue` + `src/pages/AddressCreatePage.vue` | 地址列表只读态与地址新增 create-only 交互态均已留证：`loading/empty/error/retry`、提交中禁用、防重复提交、成功跳转回列表、失败分支恢复。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/screenshots/goal4-loading-state.png`、`.../goal4-empty-state.png`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/screenshots/goal4-saving-disabled.png`、`.../goal5-after-redirect-list.png`、`.../goal6-failure-branch.png` |
| `demo-user-ui/src/router/index.ts` + `src/utils/request.ts` | `/account/addresses` 与 `/account/addresses/new` 均受 `requiresAuth` 保护，未登录重定向口径可复现。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal1_requiresAuth_guard.status=pass`） 、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal1_requiresAuth_guard_new_route.status=pass`） |

---

## 3. 地址新增 create-only 切片（本轮新增）

| 验证目标 | 2026-03-30 结论 | 证据 |
|---|---|---|
| `/account/addresses/new` requiresAuth 守卫 | pass | `.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal1_requiresAuth_guard_new_route.status=pass`） |
| 表单必填与手机号格式校验 | pass | `.../screenshots/goal2-validation-required-field.png`、`.../goal2-validation-mobile-format.png`、`.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal2_required_and_mobile_validation.status=pass`） |
| 有效提交触发 `POST /user/addresses` 并成功 | pass | `.../network/goal3-success-post-request-attempt1.json`、`.../network/goal3-success-post-response-attempt1.json` |
| 提交中禁用与防重复提交 | pass | `.../screenshots/goal4-saving-disabled.png`、`.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal4_submitting_disable_and_duplicate_prevent.status=pass`） |
| 成功后回列表并确认新增可见 | pass | `.../network/goal5-after-redirect-get-addresses-response.json`、`.../screenshots/goal5-after-redirect-list.png` |
| 失败 mock 分支：提示/恢复/不持久化 | pass | `.../network/goal6-failure-post-response.json`、`.../network/goal6-no-persist-get-response.json`、`.../screenshots/goal6-failure-branch.png` |

---

## 4. 仍保持计划态的 API 子流

| 模块 / 子流 | 当前状态 | 说明 |
|---|---|---|
| 地址编辑 `PUT /user/addresses/{id}` | 计划中 | 本轮未覆盖。 |
| 地址删除 `DELETE /user/addresses/{id}` | 计划中 | 本轮未覆盖。 |
| 设置默认地址 `PUT /user/addresses/{id}/default` | 计划中 | 本轮未覆盖。 |
| 头像上传两步链路（`upload-config -> avatar/upload`） | 计划中 | 仍未进入本轮。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 仍未进入本轮。 |

---

## 5. 边界声明

1. 本文档新增的是“地址新增 create-only 切片”运行态回填，不代表 Day02 全量完成；
2. 可升级说法：`createMyAddress（create-only）已运行验证通过`、`create-only goal1~goal6 全 pass`、`无 blocker`；
3. 不能写成“Day02 已完成并回填”或“整站联调已通过”。
