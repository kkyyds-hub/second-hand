# UserFrontDay01 API 模块规划

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`进行中（骨架已建立，待执行验证）`

---

## 1. 模块目标

把 Day01 相关能力落到明确的 API 文件、session 管理、路由守卫、错误处理和页面消费边界上。

---

## 2. Day01 重点文件

| 文件 | 角色 | Day01 要求 |
|---|---|---|
| `demo-user-ui/src/api/auth.ts` | 登录、短信、注册、激活接口入口 | 统一维护 auth 相关 URL 和参数类型，不让页面直连 URL。 |
| `demo-user-ui/src/api/seller.ts` | 首页卖家摘要接口入口 | 只负责 seller summary，不承接 auth / order / wallet 等无关请求。 |
| `demo-user-ui/src/utils/request.ts` | baseURL、请求头注入、401 清理、本地 session 读写 | 保持 `authentication` 头和本地 session 管理的一致性。 |
| `demo-user-ui/src/router/index.ts` | guestOnly / requiresAuth 路由守卫 | 未登录访问受保护页回跳登录；已登录访问 guestOnly 页面回首页。 |
| `demo-user-ui/src/layouts/UserLayout.vue` | 用户端登录后布局、导航和快捷退出 | 作为 Day01 登录后壳，不把后续业务菜单提前塞进去。 |
| `demo-user-ui/src/pages/*.vue` | Day01 页面消费层 | 页面只处理表单与展示，不重复实现 request/session 逻辑。 |

---

## 3. Day01 API 规则

1. 登录态相关能力统一通过 `src/utils/request.ts` 管理：token 保存、旧 key 兼容、当前用户保存、401 清理。
2. 登录、注册、激活页面统一只调用 `src/api/auth.ts` 暴露的方法。
3. 首页卖家摘要统一只调用 `src/api/seller.ts` 暴露的方法。
4. 页面内允许处理表单校验、按钮 loading、成功 / 失败提示，但不允许各自再实现 token 注入或 session 清理。
5. 后续业务域新增 API 时，按域拆分为新的 `src/api/*.ts`，不要继续堆到 `auth.ts` 或 `seller.ts`。

---

## 4. 当前字段与适配口径

| 模块 | 当前字段 / 适配 | 说明 |
|---|---|---|
| `auth.ts` | `AuthResponse = { token, user }` | 登录成功后直接交给 `saveUserSession`。 |
| `request.ts` | 统一解析 `{ code, msg, data }` 包装响应 | 若 `code !== 1`，统一抛错，不在页面里重复拆包。 |
| `request.ts` | 本地 session key：`user_token`、`user_profile`，兼容读取 `authentication` / `userToken` | 兼容旧 key，但主写入以 `user_token` 为准。 |
| `seller.ts` | `SellerSummary` 汇总字段 | 当前只面向首页摘要卡片消费。 |

---

## 5. Day01 预期输出

1. `auth.ts`、`seller.ts`、`request.ts`、`router/index.ts` 的职责边界写清楚。
2. 后续新增用户端业务域时，有明确的 API 模块拆分原则可复用。
3. 任何 Day01 之后的业务页，都不能绕过 `request.ts` 另起一套鉴权或错误处理逻辑。

---

## 6. Day02 以后新增模块预告

| 预计模块 | 对应业务域 | 预计归属 |
|---|---|---|
| `src/api/address.ts` | 收货地址 | UserFrontDay02 |
| `src/api/market.ts` | 市场浏览 / 商品详情 / 评论 / 举报 | UserFrontDay03 |
| `src/api/favorite.ts` | 收藏夹 | UserFrontDay03 |
| `src/api/userProduct.ts` | 用户商品管理 | UserFrontDay04 |
| `src/api/order.ts` | 买家 / 卖家订单 | UserFrontDay05 ~ UserFrontDay06 |
| `src/api/wallet.ts` / `src/api/points.ts` | 钱包 / 积分 | UserFrontDay07 |
