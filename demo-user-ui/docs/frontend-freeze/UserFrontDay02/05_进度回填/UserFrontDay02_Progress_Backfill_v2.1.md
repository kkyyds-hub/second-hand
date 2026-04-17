# UserFrontDay02 进度回填

- 日期：`2026-04-17`
- 文档版本：`v2.1`
- 当前状态：`进行中（新增：手机绑定/解绑最小运行态已确认；既有：修改密码最小运行态已确认；邮箱绑定/解绑与头像上传仍待独立 runtime 证据）`

---

## 1. 本次新增回填结论（单子流）

本次新增回填 Day02 子流：`账号安全与绑定 -> 手机绑定/解绑（/account/security/phone）`。

- 代码未变更：本次仅回填运行态事实，未新增前端/后端业务代码
- 构建已通过：`demo-user-ui` 执行 `npm.cmd run build` 成功，产物包含 `AccountPhoneBindingPage-*.js`
- 本地 real mode dev 已监听：`vite --mode real --host 0.0.0.0` 监听 `5173`
- 浏览器路由已打开：`http://127.0.0.1:5173/account/security/phone` 页面标题可见
- 请求已观察：`POST /api/user/me/bindings/phone` 与 `DELETE /api/user/me/bindings/phone`
- 写回已确认：bind/unbind 成功与失败分支的 `localStorage.user_profile.mobile` 写回行为已确认
- 运行实例已确认：前端实例为 `demo-user-ui` Vite；后端实例为 `demo-service` `com.demo.DemoApplication`
- 路由/页面/API/后端映射已对齐：本子流结论总体 `pass`
- 验证后清理已完成：临时 `5173/8080` 进程和测试脚本已清理

---

## 2. 已完成项（本轮新增）

| 项目 | 状态判断 | 证据路径 | 说明 |
|---|---|---|---|
| 手机绑定/解绑子流最小运行态闭环 | 运行态已确认（pass） | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.1.md` | `2026-04-17` 覆盖 `/account/security/phone` + `POST/DELETE /api/user/me/bindings/phone` |
| 手机绑定页路由与页面入口 | 代码已确认 | `demo-user-ui/src/router/index.ts`、`demo-user-ui/src/pages/AccountPhoneBindingPage.vue` | 路由 `account/security/phone` 与页面组件存在 |
| 手机绑定/解绑 API 映射 | 代码已确认 | `demo-user-ui/src/api/profile.ts` | 包含 `bindMyPhone` 与 `unbindMyPhone`，映射 `POST/DELETE /user/me/bindings/phone` |
| bind/unbind 写回行为 | 运行态已确认 | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.1.md` | 成功/失败分支对 `localStorage.user_profile.mobile` 的写回行为已确认 |
| 前端构建通过 | 构建已通过 | `npm.cmd run build`（`demo-user-ui`） | 本轮证据为 `2026-04-17` |

---

## 3. 仍需保留的未完成项

| 项目 | 当前判断 | 说明 |
|---|---|---|
| 邮箱绑定/解绑子流运行态验证 | 待验证 | 当前无独立 runtime pass 证据 |
| 头像上传子流运行态验证 | 待验证 | 当前仍是 `代码已确认 + 构建已通过`，尚无独立 runtime pass 证据 |
| Day02 全量收口 | 未完成 | 单子流新增 pass 不等于 Day02 已完成并回填 |

---

## 4. 口径约束

1. 本次可升级表述：`手机绑定/解绑子流最小运行态已确认（pass）`。
2. 本次必须保留表述：`邮箱绑定/解绑、头像上传尚未取得独立 runtime pass`。
3. 本次不可写成：`Day02 已完成并回填`、`账号安全与绑定全量完成`、`整站联调已通过`。

