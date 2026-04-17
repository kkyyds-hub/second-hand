# UserFrontDay02 进度回填

- 日期：`2026-04-16`
- 文档版本：`v2.0`
- 当前状态：`进行中（修改密码 current-password 子流最小运行态闭环已通过；头像上传页与邮箱绑定页代码已确认 + 构建已通过，运行态待验证）`

---

## 1. 本次闭环结论（单子流）

本次推进并回填 Day02 子流：`账号安全 -> 修改密码（current-password 路径）`。

- 已完成：真实登录拿 token、进入 `/account/security/password`、页面提交触发 `POST /api/user/me/password`、返回业务成功（`code=1`）、完成“改密后改回”闭环
- 同步确认：`/account/avatar` 与 `/account/security/email` 对应页面与路由已代码落地，且前端构建通过
- 未完成：头像上传与邮箱绑定子流运行态证据仍待补；手机绑定/解绑子流仍在计划中

---

## 2. 已完成项（本轮新增）

| 项目 | 状态判断 | 证据路径 | 说明 |
|---|---|---|---|
| 修改密码 API 从阻断改为真实提交 | 代码已确认 | `demo-user-ui/src/api/security.ts` | 实现 `POST /user/me/password`，统一 payload 归一化 |
| 密码页从“阻断态”切换为真实提交态 | 代码已确认 | `demo-user-ui/src/pages/AccountPasswordPage.vue` | 提交成功后清空表单并展示反馈 |
| 后端 password contract 兼容 old/current 两种字段 | 代码已确认 | `demo-pojo/src/main/java/com/demo/dto/user/ChangePasswordRequest.java`、`demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`、`demo-service/src/main/java/com/demo/controller/user/UserMeController.java` | `oldPassword` 优先，`currentPassword` 回退 |
| 真实登录 + 密码页进入 + POST 命中 | 运行态已确认 | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.0.md` | `2026-04-16` 最小链路：真实登录拿 token -> `/account/security/password` -> 提交触发 `POST /api/user/me/password` |
| 修改密码业务成功并完成改回闭环 | 运行态已确认 | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.0.md` | 接口返回 `code=1`，并完成“改密后改回”闭环 |
| 头像上传页代码落地 | 代码已确认 | `demo-user-ui/src/router/index.ts`、`demo-user-ui/src/pages/AccountAvatarUploadPage.vue` | `/account/avatar` 路由与页面均已存在 |
| 邮箱绑定页代码落地 | 代码已确认 | `demo-user-ui/src/router/index.ts`、`demo-user-ui/src/pages/AccountEmailBindingPage.vue` | `/account/security/email` 路由与页面均已存在 |
| 前端构建通过 | 构建已通过 | `npm.cmd run build`（`demo-user-ui`） | 2026-04-16 执行通过 |
| 后端编译通过 | 构建已通过 | `mvn -pl demo-pojo,demo-service -am -DskipTests compile`（仓库根目录） | 2026-04-16 执行通过 |

---

## 3. 未完成项（仍需保留）

| 项目 | 当前判断 | 说明 |
|---|---|---|
| 头像上传子流运行态验证 | 待验证 | 当前仅有 `代码已确认 + 构建已通过`，尚无独立运行态证据 |
| 邮箱绑定/解绑子流运行态验证 | 待验证 | 当前仅有 `代码已确认 + 构建已通过`，尚无独立运行态证据 |
| 手机绑定/解绑子流 | 计划中 | 页面/API/运行态证据尚未回填 |

---

## 4. 历史 blocker 根因修正

- 历史 blocker 更正为：`environment`
- 根因标签：`wrong-dev-instance-and-backend-not-listening`

---

## 5. 口径约束

1. 本次可升级表述：`账号安全-修改密码（current-password 路径）已代码落地、构建通过且运行态最小闭环已确认`。
2. 本次必须保留表述：`头像上传页与邮箱绑定页当前仅代码已确认 + 构建已通过，尚未升级为运行态已确认`。
3. 本次不可写成：`Day02 已完成并回填`、`整站联调已通过`。
