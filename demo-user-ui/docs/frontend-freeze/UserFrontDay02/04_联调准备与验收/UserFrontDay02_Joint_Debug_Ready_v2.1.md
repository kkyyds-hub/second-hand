# UserFrontDay02 联调准备与验收

- 日期：`2026-04-17`
- 文档版本：`v2.1`
- 当前状态：`进行中（修改密码 current-password 子流与手机绑定/解绑子流最小运行态链路已确认；邮箱绑定/解绑与头像上传仍待独立 runtime 证据）`

---

## 1. 本轮验收范围（最小）

仅覆盖 Day02 新增子流：`账号安全与绑定 -> 手机绑定/解绑` 的最小运行态链路。

- 页面：`/account/security/phone`
- API：`POST /user/me/bindings/phone`、`DELETE /user/me/bindings/phone`
- 不扩展到：邮箱绑定/解绑、头像上传、Day02 全量验收

---

## 2. 2026-04-17 本轮新增验证结果（手机绑定/解绑）

| 验证项 | 结果 | 证据 |
|---|---|---|
| 前端构建 | pass | `demo-user-ui` 执行 `npm.cmd run build` 成功，产物包含 `AccountPhoneBindingPage-*.js` |
| 本地 real mode dev 监听 | pass | `vite --mode real --host 0.0.0.0` 已监听 `5173` |
| 浏览器路由可达 | pass | 已打开 `http://127.0.0.1:5173/account/security/phone`，页面标题可见 |
| 手机绑定请求命中 | pass | 已观察到 `POST /api/user/me/bindings/phone` |
| 手机解绑请求命中 | pass | 已观察到 `DELETE /api/user/me/bindings/phone` |
| bind/unbind 写回行为 | pass | bind/unbind 成功与失败分支的 `localStorage.user_profile.mobile` 写回行为已确认 |
| 前后端实例对齐 | pass | 前端实例为 `demo-user-ui` 的 Vite 进程；后端实例为 `demo-service` 的 `com.demo.DemoApplication` |
| 路由/页面/API/后端映射对齐 | pass | `/account/security/phone` + `src/api/profile.ts` + `UserMeController` 对齐 |
| 验证后清理 | pass | 临时 `5173/8080` 进程和测试脚本已清理 |

---

## 3. 既有已确认子流（延续保留）

- `2026-04-16`：`账号安全 -> 修改密码（current-password 路径）` 最小运行态闭环已通过。
- 本次未重跑修改密码子流，仅承接既有结论。

---

## 4. 结论口径

- 本轮可声明：`手机绑定/解绑子流已达到 代码未变更 + 构建已通过 + 本地 real mode dev 已监听 + 浏览器路由可达 + POST/DELETE 请求已观察 + localStorage 写回已确认 的最小运行态 pass`
- Day02 当前可声明：`进行中（已具备账户资料编辑、地址五条切片、修改密码、手机绑定/解绑的已运行回填证据）`
- 本轮不可声明：`Day02 已完成并回填`、`账号安全与绑定全量完成`、`整站联调已通过`
- 仍需保留：`邮箱绑定/解绑` 与 `头像上传` 当前未取得独立 runtime pass

