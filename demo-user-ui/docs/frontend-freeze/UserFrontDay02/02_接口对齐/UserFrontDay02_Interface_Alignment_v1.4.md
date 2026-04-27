# UserFrontDay02 前后端接口对齐

- 日期：`2026-04-22`
- 文档版本：`v1.4`
- 当前状态：`已完成并回填（Day02 owned scope contract truth 已完成最终对齐复核）`

---

## 1. 本轮对齐结论

本轮不新增接口改造，只复核 Day02 owned scope 的既有 contract 真值，并基于既有代码 / controller / runtime / docs 证据完成最终口径统一。

结论：`UserMeController`、`AddressController` 与 Day02 前端消费面当前不存在新的未消化 contract 冲突，Day02 可进入 `已完成并回填`。

---

## 2. Day02 contract 面总表

| 子域 | 前端消费面 | 后端入口 | 当前结论 | 备注 |
|---|---|---|---|---|
| 资料编辑 | `src/api/profile.ts`、`AccountCenterPage.vue` | `PATCH /user/me/profile` | `pass` | 昵称 / 简介编辑与 session 回写已回填 |
| 头像上传配置 | `src/api/profile.ts` | `POST /user/me/upload-config` | `pass` | 上传配置获取成功 |
| 头像上传写路径 | `AccountAvatarUploadPage.vue`、`src/api/profile.ts` | `PUT /user/me/avatar/upload` | `pass` | 历史 `backend / contract-gap` 已关闭 |
| 头像资料写回 | `src/api/profile.ts` | `PATCH /user/me/profile` | `pass` | 上传后资料写回已命中 |
| 修改密码 | `src/api/security.ts`、`AccountPasswordPage.vue` | `POST /user/me/password` | `pass` | `oldPassword/currentPassword` 双口径兼容已生效 |
| 手机绑定 | `src/api/profile.ts`、`AccountPhoneBindingPage.vue` | `POST /user/me/bindings/phone` | `pass` | bind 成功/失败分支与写回行为已确认 |
| 手机解绑 | `src/api/profile.ts`、`AccountPhoneBindingPage.vue` | `DELETE /user/me/bindings/phone` | `pass` | unbind 成功/失败分支与写回行为已确认 |
| 邮箱绑定 | `src/api/profile.ts`、`AccountEmailBindingPage.vue` | `POST /user/me/bindings/email` | `pass` | 受控验证码链路已留证 |
| 邮箱解绑 | `src/api/profile.ts`、`AccountEmailBindingPage.vue` | `DELETE /user/me/bindings/email` | `pass` | success/failure 分支均已留证 |
| 地址列表 | `src/api/address.ts`、`AddressListPage.vue` | `GET /user/addresses` | `pass` | 只读起步链路已留证 |
| 地址新增 | `src/api/address.ts`、`AddressCreatePage.vue` | `POST /user/addresses` | `pass` | create-only 已留证 |
| 地址编辑 | `src/api/address.ts`、`AddressEditPage.vue` | `PUT /user/addresses/{id}` | `pass` | edit-only 已留证 |
| 默认地址切换 | `src/api/address.ts`、`AddressListPage.vue` | `PUT /user/addresses/{id}/default` | `pass` | 浏览器可控 mock 运行态 |
| 地址删除 | `src/api/address.ts`、`AddressListPage.vue` | `DELETE /user/addresses/{id}` | `pass` | 浏览器可控 mock 运行态 |

---

## 3. 历史问题关闭情况

1. 修改密码历史 `oldPassword/currentPassword` 口径不一致：已通过 `src/api/security.ts` + `ChangePasswordRequest.java` + `UserServiceImpl.java` 的兼容收敛关闭；
2. 头像上传历史 `backend / contract-gap`：已通过 `demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java` 的最小正确层修复关闭；
3. 本轮未发现新的 request-layer / controller-layer / DTO-layer 冲突，不触发 `$drive-demo-user-ui-delivery`。

---

## 4. 必须保留的接口边界

1. 地址 `edit-only / set-default / delete-only` 的通过结论属于 Day02 day-level acceptance 可接受的 running evidence，但不等于整站真实联调通过；
2. `authentication` 仍是用户端请求头口径，不改写为 `Authorization: Bearer`；
3. Day02 contract 完成不外推 Day04+ 业务域。

---

## 5. 关键证据入口

- `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.5.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay02/05_进度回填/UserFrontDay02_Progress_Backfill_v2.5.md`
- `demo-user-ui/.tmp_runtime/2026-04-16-userfront-day02-password-minimal-runtime/summary.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-email-avatar-runtime-v3/email-binding/email-minimal-chain-result.json`
- `demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/userfront-day02-avatar-minimal-runtime.json`
