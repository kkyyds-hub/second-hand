# UserFrontDay01 联调准备与验收

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`进行中（已建立验收清单，待执行）`

---

## 1. 环境前置条件

1. 后端服务可访问，默认以 `http://localhost:8080` 为准。
2. 前端以 `demo-user-ui` 项目运行，常用命令为：
   - `npm install`
   - `npm run dev`
   - `npm.cmd run build`
3. `vite.config.ts` 的 `/api` 代理应指向 `http://localhost:8080`。
4. 用户端鉴权请求头当前固定为 `authentication`。
5. 若要验证邮箱激活预览，需要 `VITE_EMAIL_PREVIEW_PATH` 可访问。

---

## 2. Day01 最小验证清单

1. 未登录直接访问 `/`，应跳回 `/login?redirect=...`。
2. 登录成功后，应保存本地 session 并进入 `/`。
3. 已登录访问 `/login`、`/register/phone`、`/register/email`、`/activate/email`，应回到首页。
4. 手机注册页应能触发 `POST /user/auth/sms/send` 与 `POST /user/auth/register/phone`。
5. 邮箱注册页应能触发 `POST /user/auth/register/email`，并可打开邮件预览辅助入口。
6. 邮箱激活页应支持 query token 自动激活和手动 token 激活。
7. 登录后首页应尝试拉取 `GET /user/seller/summary`。
8. 点击退出登录或进入 `/logout` 后，应清理本地 session 并返回登录页。
9. 若后端返回 401，应清理本地 session 并回到登录页。

---

## 3. 当日需要保留的证据

1. `npm.cmd run build` 的执行结果。
2. 登录成功或失败时的请求 / 响应记录。
3. 本地 `user_token` / `authentication` / `user_profile` 写入结果。
4. 未登录拦截、已登录重定向、退出登录和 401 清理结果。
5. 首页卖家摘要成功 / 失败时的接口返回或错误提示。
6. 手机注册、邮箱注册、邮箱激活的最小执行记录。

---

## 4. 验收判定建议

1. 能实际执行的项，优先记录为 `运行态已确认`。
2. 只有代码存在或 build 通过的项，记录为 `代码已确认` / `构建已通过`。
3. 若某条链路失败，必须写明是前端问题、后端问题还是环境阻塞。
4. 若邮件预览只能打开辅助页，不能因此把“邮箱注册全链路”直接写成已完成并回填。

---

## 5. 当前已知待验证点

- 当前文档只确认了 build 成功，尚未在本日文档里落真实浏览器运行证据。
- 登录、注册、激活、seller summary、401 清理都应在后续回填中逐项补证据，而不是一次性笼统写通过。
