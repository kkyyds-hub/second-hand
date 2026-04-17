# UserFrontDay02 API 模块规划

- 日期：`2026-03-31`
- 文档版本：`v1.7`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片均已运行回填；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，当前已形成四个“已运行验证”的最小切片：

1. `AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`
2. `地址管理只读起步切片（/account/addresses + GET /user/addresses + loading/empty/error/retry）`
3. `地址新增 create-only 切片（/account/addresses/new + POST /user/addresses + 成功跳转 + 失败不持久化）`
4. `地址编辑 edit-only 切片（/account/addresses/:id/edit + GET /user/addresses/{id} + PUT /user/addresses/{id} + 成功回列表 + 失败不持久化）`

本版是 docs 诚实回填：本轮新增的是“浏览器可控 mock 路由”下的 edit-only 运行态证据，不等于后端真实联调已通过。

---

## 2. 已落地 API 结论（2026-03-31）

| 模块 / 文件 | 当前结论 | 关键证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | `updateMyProfile` 最小链路与同切片 focused regression 继续稳定。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`、`.../userfront-day02-account-profile-focused-regression.json` |
| `demo-user-ui/src/api/address.ts` | `getMyAddressList`（只读）、`createMyAddress`（create-only）、`getMyAddressDetail` 与 `updateMyAddress`（edit-only）均已在浏览器链路完成运行态验证；其中 edit-only 结论来自浏览器可控 mock 路由，不是后端真实联调通过结论。 | `demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/summary.md`、`.../userfront-day02-address-edit-only-minimal-runtime.json`、`.../network/goal2-detail-get-response-attempt1.json`、`.../network/goal4-put-update-response-attempt1.json`、`.../network/goal6-address-write-ops-observation.json` |
| `demo-user-ui/src/pages/AddressListPage.vue` + `src/pages/AddressCreatePage.vue` + `src/pages/AddressEditPage.vue` | 地址只读、create-only、edit-only 交互态均已留证：详情回填、必填/手机号校验、提交中禁用、防重复提交、成功回列表、失败分支恢复且不持久化。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/summary.md`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/summary.md`、`demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/summary.md`、`.../screenshots/goal2-before-edit-prefill.png`、`.../screenshots/goal3-saving-disabled.png`、`.../screenshots/goal5-failure-branch.png` |
| `demo-user-ui/src/router/index.ts` + `src/utils/request.ts` | `/account/addresses`、`/account/addresses/new`、`/account/addresses/:id/edit` 均受 `requiresAuth` 保护；未登录重定向口径可复现。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/userfront-day02-address-readonly-minimal-runtime.json`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/userfront-day02-address-create-only-minimal-runtime.json`、`demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal1_requiresAuth_guard_edit_route.status=pass`） |

---

## 3. 地址编辑 edit-only 切片（本轮新增）

| 验证目标 | 2026-03-31 结论 | 证据 |
|---|---|---|
| `/account/addresses/:addressId/edit` requiresAuth 守卫 | pass | `.../userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal1_requiresAuth_guard_edit_route.status=pass`） |
| 编辑页 `GET /user/addresses/{id}` 详情回填 | pass | `.../network/goal2-detail-get-request-attempt1.json`、`.../network/goal2-detail-get-response-attempt1.json`、`.../screenshots/goal2-before-edit-prefill.png` |
| 必填/手机号校验 + 提交中禁用/防重复 | pass | `.../screenshots/goal3-validation-required-mobile.png`、`.../screenshots/goal3-saving-disabled.png`、`.../userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal3_validation_and_submitting_guard.status=pass`） |
| `PUT /user/addresses/{id}` 成功后回列表并可见更新 | pass | `.../network/goal4-put-update-request-attempt1.json`、`.../network/goal4-put-update-response-attempt1.json`、`.../network/goal4-after-redirect-list-response.json`、`.../screenshots/goal4-after-redirect-list.png` |
| 失败分支提示/恢复/不持久化 | pass | `.../network/goal5-put-update-response-attempt2.json`、`.../network/goal5-no-persist-list-response.json`、`.../screenshots/goal5-failure-branch.png` |
| 本轮无 create/delete/default 写操作 | pass | `.../network/goal6-address-write-ops-observation.json`、`.../userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal6_no_create_delete_default_write_ops.status=pass`） |

---

## 4. 仍保持计划态的 API 子流

| 模块 / 子流 | 当前状态 | 说明 |
|---|---|---|
| 地址删除 `DELETE /user/addresses/{id}` | 计划中 | 本轮未覆盖。 |
| 设置默认地址 `PUT /user/addresses/{id}/default` | 计划中 | 本轮未覆盖。 |
| 头像上传两步链路（`upload-config -> avatar/upload`） | 计划中 | 仍未进入本轮。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 仍未进入本轮。 |

---

## 5. 边界声明

1. 本文档 `v1.7` 新增的是“地址编辑 edit-only 切片”运行态回填，不代表 Day02 全量完成；
2. 可升级说法：`getMyAddressDetail/updateMyAddress（edit-only）已运行验证通过`、`edit-only goal1~goal6 全 pass`、`blocker=null`；
3. 必须保留表述：`本轮为浏览器可控 mock 运行态验证，不是后端真实联调通过结论`；
4. 不能写成“Day02 已完成并回填”或“整站联调已通过”。
