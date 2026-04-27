# UserFrontDay02 API 模块规划

- 日期：`2026-04-22`
- 文档版本：`v2.1`
- 当前状态：`已完成并回填（Day02 API module consumption boundary 已完成终裁复核）`

---

## 1. 最终模块结论

Day02 owned scope 的 API 消费边界稳定落在三个模块：

1. `src/api/profile.ts`
2. `src/api/security.ts`
3. `src/api/address.ts`

本轮 docs-only 终裁复核未发现页面绕过 API 模块直调、重复适配漂移或新的 contract 断裂点，因此 Day02 API 模块口径可随 Day02 一并升级为 `已完成并回填`。

---

## 2. 模块与页面消费边界

| 模块 | 负责能力 | 页面消费面 | 当前状态 |
|---|---|---|---|
| `profile.ts` | 当前用户资料、头像上传配置、头像资料写回、手机/邮箱绑定解绑 | `AccountCenterPage.vue`、`AccountAvatarUploadPage.vue`、`AccountPhoneBindingPage.vue`、`AccountEmailBindingPage.vue` | `已完成并回填` |
| `security.ts` | 修改密码 payload 归一、`oldPassword/currentPassword` 兼容映射 | `AccountPasswordPage.vue` | `已完成并回填` |
| `address.ts` | 地址列表、创建、编辑、删除、默认地址切换 | `AddressListPage.vue`、`AddressCreatePage.vue`、`AddressEditPage.vue` | `已完成并回填` |

---

## 3. 已完成的模块级事实

1. `profile.ts` 已覆盖资料编辑、头像上传两步链路后的资料写回，以及手机/邮箱绑定解绑的接口消费；
2. `security.ts` 已把密码页表单语义与后端 `oldPassword/currentPassword` 兼容口径收敛在 API 层；
3. `address.ts` 已承接地址域五个最小切片的统一消费边界；
4. 路由 / 页面 / API / controller 映射在 Day02 owned scope 内已形成可追溯闭环。

---

## 4. 仍需显式声明的边界

1. Day02 API 模块完成不等于整站 API 治理完成，Day08 的共享 API 模块整治仍保留；
2. 地址 `edit-only / set-default / delete-only` 的 day-level running evidence 已被 Day02 acceptance 接纳，但不能被表述为整站真实联调通过；
3. 若后续 Day04+ 复用 `profile.ts`、`security.ts`、`address.ts` 时发生 contract 变更，应先回填再调整归属文档。

---

## 5. 关键证据入口

- `demo-user-ui/docs/frontend-freeze/UserFrontDay02/02_接口对齐/UserFrontDay02_Interface_Alignment_v1.4.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.5.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay02/05_进度回填/UserFrontDay02_Progress_Backfill_v2.5.md`
