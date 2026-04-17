# UserFrontDay02 前后端接口对齐

- 日期：`2026-04-16`
- 文档版本：`v1.3`
- 当前状态：`进行中（本次仅推进“修改密码”子流：代码已确认 + 构建已通过，运行态待验证）`

---

## 1. 本次闭环范围（单子流）

仅覆盖 Day02 子流：`账号安全 -> 修改密码`。

- 页面入口：`/account/security/password`
- 页面文件：`src/pages/AccountPasswordPage.vue`
- API 边界：`src/api/security.ts`
- 后端入口：`POST /user/me/password`（`UserMeController.changePassword`）

---

## 2. 密码修改契约结论（v1.3）

| 项 | 结论 | 备注 |
|---|---|---|
| Endpoint | `POST /user/me/password` | 保持不变 |
| 必填提交 | `newPassword` | 由后端 DTO 校验必填 |
| 旧密码口径 | `oldPassword` 为主，`currentPassword` 兼容 | 前端 API 层同时提交两者；后端 Service 做回退兼容 |
| 验证码口径 | `verifyChannel + code` 仍可用 | 本次页面不暴露该分支，只保留当前密码路径 |
| 前端确认字段 | `confirmPassword` 仅前端校验 | 不下发后端 |

---

## 3. 问题归类与根因

- `frontend`：密码页已存在，但 `src/api/security.ts` 之前为硬阻断实现。
- `contract`：前端表单使用 `currentPassword` 语义，后端历史口径为 `oldPassword`。
- `backend`：Service 仅读取 `oldPassword`，缺少 `currentPassword` 兼容回退。
- `environment`：本机 `localhost:8080/5173` 均未监听，运行态验证无法在本轮完成。

结论：本问题属于 `mixed（frontend + contract + backend + environment）`。

---

## 4. 本轮对齐后的边界规则

1. 页面层只负责表单态、提示态，不拼接后端字段细节。
2. `src/api/security.ts` 统一做 payload 归一化与兼容映射。
3. 后端 `ChangePasswordRequest + UserServiceImpl` 同时接受 `oldPassword/currentPassword`。
4. 未拿到运行态证据前，不把本子流写成“联调通过”。

---

## 5. 本轮未覆盖项

- 手机绑定/解绑
- 邮箱绑定/解绑
- 修改密码的 `verifyChannel + code` 页面化交互
- 真实运行态（浏览器链路 + 后端联调）证据
