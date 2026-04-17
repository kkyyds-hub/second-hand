# UserFrontDay02 API 模块规划

- 日期：`2026-04-04`
- 文档版本：`v1.8`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片均已运行回填；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，当前已形成五个“已运行验证”的最小切片：

1. `AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`
2. `地址管理只读起步切片（/account/addresses + GET /user/addresses + loading/empty/error/retry）`
3. `地址新增 create-only 切片（/account/addresses/new + POST /user/addresses + 成功跳转 + 失败不持久化）`
4. `地址编辑 edit-only 切片（/account/addresses/:id/edit + GET /user/addresses/{id} + PUT /user/addresses/{id} + 成功回列表 + 失败不持久化）`
5. `地址默认切换 set-default 切片（/account/addresses + PUT /user/addresses/{id}/default + 成功后刷新 GET + 失败恢复）`

本版是 docs 诚实回填：本轮新增的是 set-default 运行态证据；结论来自浏览器可控 mock 路由，不等于后端真实联调已通过。

---

## 2. 已落地 API 结论（2026-04-04）

| 模块 / 文件 | 当前结论 | 关键证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | `updateMyProfile` 最小链路与同切片 focused regression 继续稳定。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`、`.../userfront-day02-account-profile-focused-regression.json` |
| `demo-user-ui/src/api/address.ts` | `getMyAddressList`（只读）、`createMyAddress`（create-only）、`getMyAddressDetail` / `updateMyAddress`（edit-only）与 `setMyDefaultAddress`（set-default）均已完成最小运行验证；其中 edit-only 与 set-default 结论来自浏览器可控 mock 路由。 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/summary.md`、`.../userfront-day02-address-set-default-minimal-runtime.json`、`.../network/goal2-put-default-success-response.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../network/goal5-put-default-failure-response.json` |
| `demo-user-ui/src/pages/AddressListPage.vue` | 地址列表页 set-default 交互态已留证：提交中禁用、防重复点击、成功提示 + 刷新后默认标识切换、失败提示与状态恢复。 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/summary.md`、`.../screenshots/before.png`、`.../screenshots/saving.png`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png`、`.../screenshots/failure.png` |
| `demo-user-ui/src/router/index.ts` + `src/utils/request.ts` | `/account/addresses` 受 `requiresAuth` 保护；本轮在登录后进入地址列表页并执行 set-default 验证。 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/userfront-day02-address-set-default-minimal-runtime.json` |

---

## 3. 地址默认切换 set-default 切片（本轮新增）

| 验证目标 | 2026-04-04 结论 | 证据 |
|---|---|---|
| goal1：地址列表页可达，默认地址标识可见 | pass | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/screenshots/before.png`、`.../userfront-day02-address-set-default-minimal-runtime.json`（`flows.goal1_address_list_reachable_default_badge_visible.status=pass`） |
| goal2：非默认地址点击后触发 `PUT /user/addresses/{id}/default` | pass | `.../network/goal2-put-default-success-request.json`、`.../network/goal2-put-default-success-response.json` |
| goal3：提交中禁用 + 防重复点击 | pass | `.../screenshots/saving.png`、`.../userfront-day02-address-set-default-minimal-runtime.json`（`flows.goal3_submitting_disable_and_duplicate_prevent.status=pass`） |
| goal4：成功后自动刷新列表 + 默认标识切换可见 | pass | `.../network/goal4-get-refresh-after-success-request.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png` |
| goal5：失败分支提示 + 状态恢复 + 不错误持久化 | pass | `.../network/goal5-put-default-failure-response.json`、`.../network/goal5-get-refresh-after-failure-response.json`、`.../screenshots/failure.png` |
| goal6：本轮不执行 delete/create/edit 写操作 | pass | `.../network/goal6-write-op-boundary-observation.json`（`disallowedWriteCount=0`） |

---

## 4. 仍保持计划态的 API 子流

| 模块 / 子流 | 当前状态 | 说明 |
|---|---|---|
| 地址删除 `DELETE /user/addresses/{id}` | 计划中 | 本轮未覆盖。 |
| 头像上传两步链路（`upload-config -> avatar/upload`） | 计划中 | 仍未进入本轮。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 仍未进入本轮。 |

---

## 5. 边界声明

1. 本文档 `v1.8` 新增的是“地址默认切换 set-default 切片”运行态回填，不代表 Day02 全量完成；
2. 可升级说法：`setMyDefaultAddress 已运行验证通过`、`set-default goal1~goal6 全 pass`、`blocker=null`；
3. 必须保留表述：`本轮为浏览器可控 mock 运行态验证，不是后端真实联调通过结论`；
4. 不能写成“Day02 已完成并回填”或“整站联调已通过”。
