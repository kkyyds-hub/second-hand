# UserFrontDay02 联调准备与验收

- 日期：`2026-04-22`
- 文档版本：`v2.5`
- 当前状态：`已完成并回填（Day02 final acceptance docs-only adjudication 完成）`

---

## 1. 本轮定位

本轮不新增联调动作，不重跑 build/dev/browser/runtime；仅基于 Day02 既有运行证据、joint-debug 记录、代码面与 controller 面做最终 acceptance docs-only adjudication。

---

## 2. Day02 关键子流终裁表

| 子流 | 关键事实 | 证据等级 | 结论 |
|---|---|---|---|
| 账户资料编辑 focused regression | `PATCH /user/me/profile` + session 回写已留证 | 代码已确认 + 构建已通过 + 运行态已确认 | `pass` |
| 地址只读起步 | `/account/addresses` + `GET /user/addresses` 最小只读链路已留证 | 代码已确认 + 构建已通过 + 运行态已确认 | `pass` |
| 地址新增 create-only | `POST /user/addresses` 成功/失败分支已留证 | 代码已确认 + 构建已通过 + 运行态已确认 | `pass` |
| 地址编辑 edit-only | 详情回填 + `PUT` 已留证 | 代码已确认 + 构建已通过 + 运行态已确认（浏览器可控 mock） | `pass` |
| 地址默认切换 set-default | `PUT /user/addresses/{id}/default` 已留证 | 代码已确认 + 构建已通过 + 运行态已确认（浏览器可控 mock） | `pass` |
| 地址删除 delete-only | `DELETE /user/addresses/{id}` 已留证 | 代码已确认 + 构建已通过 + 运行态已确认（浏览器可控 mock） | `pass` |
| 修改密码 current-password | 真实登录 token -> `POST /api/user/me/password` -> 改密后改回闭环已留证 | 代码已确认 + 构建已通过 + 运行态已确认 | `pass` |
| 手机绑定/解绑 | `/account/security/phone` + `POST/DELETE /api/user/me/bindings/phone` + `localStorage.user_profile.mobile` 写回已确认 | 代码已确认 + 构建已通过 + 运行态已确认（joint-debug） | `pass` |
| 邮箱绑定/解绑 | `/account/security/email` + `POST/DELETE /api/user/me/bindings/email` + `localStorage.user_profile.email` 写回已确认 | 代码已确认 + 构建已通过 + 运行态已确认 | `pass` |
| 头像上传 | `upload-config -> PUT upload -> PATCH profile` + `Avatar updated.` + session/avatar 写回已确认 | 代码已确认 + 构建已通过 + 运行态已确认 | `pass` |

---

## 3. 终裁判断

1. Day02 当前 owned scope 内未发现新的关键子流证据缺口；
2. 历史头像上传 `backend / contract-gap` 已关闭，不再构成阻断 Day02 完成的 blocker；
3. 地址 `edit-only / set-default / delete-only` 的 mock 运行态边界已在既有文档中充分声明，属于 Day02 day-level acceptance 可接受边界；
4. 因此 Day02 可由 `已具备收口材料，待最终裁定` 升级为 `已完成并回填`。

---

## 4. 核心证据路径

- `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`
- `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/summary.md`
- `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/summary.md`
- `demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/summary.md`
- `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/summary.md`
- `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/summary.md`
- `demo-user-ui/.tmp_runtime/2026-04-16-userfront-day02-password-minimal-runtime/summary.json`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.1.md`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-email-avatar-runtime-v3/email-binding/email-minimal-chain-result.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/userfront-day02-avatar-minimal-runtime.json`

---

## 5. 升级路由判断（$drive-demo-user-ui-delivery）

- 结论：`不触发升级`。
- 理由：本轮复核未发现新的 code / contract / controller / runtime 真值冲突；历史头像上传跨层问题已在既有证据中关闭。

---

## 6. 必须保留的边界

1. Day02 `已完成并回填` 不等于整站联调已通过；
2. Day02 完成不外推 Day04+；
3. Day02 退出当前执行日后，root 应顺延至当前最早未收口的 `UserFrontDay04`。
