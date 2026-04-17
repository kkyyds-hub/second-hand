# UserFrontDay02 API 模块规划

- 日期：`2026-04-04`
- 文档版本：`v1.9`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片 + 地址删除 delete-only 切片均已运行回填；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，当前已形成六个“已运行验证”的最小切片：

1. `AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`
2. `地址管理只读起步切片（/account/addresses + GET /user/addresses + loading/empty/error/retry）`
3. `地址新增 create-only 切片（/account/addresses/new + POST /user/addresses + 成功跳转 + 失败不持久化）`
4. `地址编辑 edit-only 切片（/account/addresses/:id/edit + GET /user/addresses/{id} + PUT /user/addresses/{id} + 成功回列表 + 失败不持久化）`
5. `地址默认切换 set-default 切片（/account/addresses + PUT /user/addresses/{id}/default + 成功后刷新 GET + 失败恢复）`
6. `地址删除 delete-only 切片（/account/addresses + DELETE /user/addresses/{id} + 成功后刷新 GET + 失败恢复 + 与 set-default 互斥）`

本版是 docs 诚实回填：本轮新增的是 delete-only 运行态证据；结论来自浏览器可控 mock 路由，不等于后端真实联调已通过。

---

## 2. 已落地 API 结论（2026-04-04）

| 模块 / 文件 | 当前结论 | 关键证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | `updateMyProfile` 最小链路与同切片 focused regression 继续稳定。 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`、`.../userfront-day02-account-profile-focused-regression.json` |
| `demo-user-ui/src/api/address.ts` | `getMyAddressList`（只读）、`createMyAddress`（create-only）、`getMyAddressDetail` / `updateMyAddress`（edit-only）、`setMyDefaultAddress`（set-default）与 `deleteMyAddress`（delete-only）均已完成最小运行验证；其中 edit-only / set-default / delete-only 结论来自浏览器可控 mock 路由。 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/summary.md`、`.../userfront-day02-address-delete-only-minimal-runtime.json`、`.../network/goal2-delete-success-response.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../network/goal5-delete-failure-response.json` |
| `demo-user-ui/src/pages/AddressListPage.vue` | 地址列表页 delete-only 交互态已留证：删除中禁用、防重复点击、成功提示 + 刷新后目标项消失、失败提示与状态恢复、与 set-default 互斥禁用。 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/summary.md`、`.../screenshots/before.png`、`.../screenshots/deleting.png`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png`、`.../screenshots/failure.png` |
| `demo-user-ui/src/router/index.ts` + `src/utils/request.ts` | `/account/addresses` 受 `requiresAuth` 保护；本轮在登录后进入地址列表页并执行 delete-only 验证。 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/userfront-day02-address-delete-only-minimal-runtime.json` |

---

## 3. 地址删除 delete-only 切片（本轮新增）

| 验证目标 | 2026-04-04 结论 | 证据 |
|---|---|---|
| goal1：地址列表页可达，删除按钮显示条件正确（`id!=null` 可删） | pass | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/screenshots/before.png`、`.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal1_address_list_reachable_delete_button_condition.status=pass`） |
| goal2：点击删除触发 `DELETE /user/addresses/{id}` | pass | `.../network/goal2-delete-success-request.json`、`.../network/goal2-delete-success-response.json` |
| goal3：删除中禁用 + 防重复点击 | pass | `.../screenshots/deleting.png`、`.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal3_deleting_disable_and_duplicate_prevent.status=pass`） |
| goal4：成功后自动刷新列表，目标项消失/数量变化可见 | pass | `.../network/goal4-get-refresh-after-success-request.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png` |
| goal5：失败分支提示 + 状态恢复 + 不错误持久化 | pass | `.../network/goal5-delete-failure-response.json`、`.../network/goal5-get-refresh-after-failure-response.json`、`.../screenshots/failure.png` |
| goal6：与 set-default 的互斥禁用（并发写抑制） | pass | `.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal6_delete_set_default_mutex_disable.status=pass`、`setDefaultPutCount=0`）、`.../network/unexpected-address-write-observation.json` |

---

## 4. 仍保持计划态的 API 子流

| 模块 / 子流 | 当前状态 | 说明 |
|---|---|---|
| 头像上传两步链路（`upload-config -> avatar/upload`） | 计划中 | 仍未进入本轮。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 仍未进入本轮。 |

---

## 5. 边界声明

1. 本文档 `v1.9` 新增的是“地址删除 delete-only 切片”运行态回填，不代表 Day02 全量完成；
2. 可升级说法：`deleteMyAddress 已运行验证通过`、`delete-only goal1~goal6 全 pass`、`blocker=null`；
3. 必须保留表述：`本轮为浏览器可控 mock 运行态验证，不是后端真实联调通过结论`；
4. 不能写成“Day02 已完成并回填”或“整站联调已通过”。
