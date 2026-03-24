# UserFrontDay01 API 模块计划

- 日期：`2026-03-18`
- 文档版本：`v1.1`
- 当前状态：`进行中（已补 auth/header/401 与 seller summary 的真实联调口径）`

---

## 1. Day01 模块边界

| 文件 | 角色 | 本轮结论 |
|---|---|---|
| `src/utils/request.ts` | 注入鉴权头、统一 unwrap、401 清 session、redirect 规则 | 继续保持 Day01 的唯一鉴权入口；真实 backend 已确认要注入 `authentication` |
| `src/api/auth.ts` | 登录 / 注册 / 激活相关 API | 仍负责 auth 相关 URL 与返回归一化；本轮没有新增 Day02+ 领域 API |
| `src/api/seller.ts` | 首页 seller summary 读接口 | 继续只负责 `GET /user/seller/summary` 与字段归一化 |
| `src/router/index.ts` | `guestOnly` / `requiresAuth` 路由守卫 | 不承担 header 注入；只负责页面级访问控制 |
| `src/pages/HomePage.vue` | 首页摘要消费层 | 只维护 loading / error / retry；本轮补了失败态文案，避免接口失败时仍显示“已加载” |

---

## 2. 本轮确认的 request / auth 事实

1. `request.ts` 中的 token 注入位置正确，仍应保留在请求拦截器；
2. 对真实 backend 而言，`authentication` 是有效头，`Authorization` 不是 Day01 当前协议；
3. 401 时由 `request.ts` 统一清理 `user_token` / `authentication` / `user_profile`，并对受保护页面回跳 `/login?redirect=...`；
4. `HomePage.vue` 不应该自行拼鉴权头，也不应该直接处理 401 清 session；
5. seller summary 的字段别名与数字归一化继续留在 `src/api/seller.ts`，页面不散落兜底逻辑。

---

## 3. 本轮前端改动

### `src/pages/HomePage.vue`

新增 `summaryStatusText` 计算属性，优先展示失败态：

- loading 时：`正在同步最新卖家摘要...`
- error 时：`卖家摘要加载失败，请检查当前登录态或接口状态后重试。`
- 首次成功后：`卖家摘要已加载，可手动刷新。`

这样可以避免接口失败后顶部状态仍然显示“已加载”，影响 Day01 联调判断。

---

## 4. Day01 后续仍保持的 API 约束

1. 不把 seller summary 塞回 `auth.ts`；
2. 不把 `authentication` 改写成 `Authorization: Bearer`；
3. 不在页面层自行清 session 或读写多套 token key；
4. 不因为这次 seller summary 查清了，就提前展开 Day02+ 模块拆分；
5. 账户中心在 Day01 仍只读本地 session 快照，不虚构新的 profile 查询接口。

---

## 5. 当前剩余缺口

- 真实 `/login` 成功返回 `token + user` 的浏览器链路仍未留证；
- seller summary 虽已用有效 seller JWT 验证可达，但还没有“真实登录成功后直接进入首页”的证据；
- 登录、注册、激活、退出仍然是 Day01 后续运行态补证对象。
