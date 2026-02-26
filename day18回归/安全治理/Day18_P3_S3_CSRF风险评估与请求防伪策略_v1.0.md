# Day18 P3-S3 CSRF 风险评估与请求防伪策略 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P3-S3：CSRF 风险说明与请求防伪策略定版`
- 目标：对当前 JWT 架构下的 CSRF 风险边界给出统一结论，并形成可直接评审的接口判定规则。

---

## 1. 代码事实基线（当前实现）

## 1.1 鉴权载体与拦截范围

1. 当前鉴权主要依赖 JWT Header：
   - 管理端：`demo.jwt.admin-token-name = token`
   - 用户端：`demo.jwt.user-token-name = authentication`
2. 拦截器范围：
   - 管理端：`/admin/**`，排除 `/admin/employee/login`
   - 用户端：`/user/**`，排除 `/user/auth/**`、`/user/shop/status`
3. 代码证据：
   - `demo-service/src/main/resources/application.yml`
   - `demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java`
   - `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`
   - `demo-service/src/main/java/com/demo/interceptor/JwtTokenAdminInterceptor.java`

## 1.2 当前匿名写接口（白名单）

1. `POST /user/auth/**`（注册/登录/验证码等认证入口）
2. `POST /admin/employee/login`（管理端登录）
3. `POST /payment/callback`（第三方支付回调，业务签名与时间戳校验）
4. 说明：
   - `GET /dev/token/safe-token` 仅 `dev` profile 可用，不属于写接口。

---

## 2. CSRF 风险边界结论（冻结）

## 2.1 为什么当前 Header Token 架构默认风险较低

1. 浏览器跨站表单/图片请求不会自动附带自定义 Header（如 `authentication`、`token`）。
2. 当前业务鉴权依赖 Header Token，而非 cookie/session 自动携带。
3. 因此经典 CSRF（借助浏览器自动携带凭证）在当前主链路下默认难以成立。

## 2.2 当前仍需关注的关联风险（非 CSRF 主体）

1. XSS 风险：若前端出现 XSS，可窃取 token 后发起伪造请求（这是 XSS 导致的会话劫持，不是经典 CSRF）。
2. CORS 配置风险：若未来错误放开跨域并允许敏感 Header，可能放大跨站调用风险。
3. 鉴权载体漂移：若后续改为 cookie 自动携带而不加 CSRF 防护，风险模型会立刻变化。

## 2.3 团队统一判定口径

1. 当前模式（Header JWT + 无 cookie 会话）：
   - 默认判定：`CSRF 低风险`
   - 必做：保持 Header Token，不引入“自动携带凭证”鉴权
2. 未来若引入 cookie/session：
   - 立即判定：`必须启用 CSRF Token 防护`

---

## 3. cookie/session 场景可选 CSRF 方案

## 3.1 方案 A：Synchronizer Token Pattern（推荐用于 session 架构）

1. 服务端为会话生成 CSRF Token（会话内保存）。
2. 前端提交写请求时在 Header 中携带 `X-CSRF-Token`。
3. 服务端校验会话中的 Token 与请求 Token 一致性。

适用：
1. 使用服务端会话（HttpSession/Redis Session）体系。

## 3.2 方案 B：Double Submit Cookie（推荐用于无状态 cookie JWT）

1. 服务端下发 `csrf_token` cookie（非 HttpOnly）和鉴权 cookie（HttpOnly）。
2. 前端把 `csrf_token` 同步放入 `X-CSRF-Token` Header。
3. 服务端比较 cookie 值与 header 值，一致才放行。

适用：
1. 无状态架构但使用 cookie 自动携带鉴权凭证。

## 3.3 配套强制项

1. Cookie 策略：`SameSite=Lax/Strict`、`Secure`、`HttpOnly`（鉴权 cookie）。
2. 跨域策略：`CORS origin` 精确白名单，禁止 `*` + 凭证同时启用。
3. 服务端附加校验：`Origin/Referer` 校验（关键资金/账号接口建议启用）。

---

## 4. 接口安全清单（评审判定版）

## 4.1 必须 Header Token 的接口范围

1. `/user/**` 除白名单外所有写接口（POST/PUT/PATCH/DELETE）必须携带 `authentication`。
2. `/admin/**` 除 `/admin/employee/login` 外所有写接口必须携带 `token`。

## 4.2 允许匿名写接口（仅白名单）

1. `/user/auth/**`：认证入口，允许匿名。
2. `/admin/employee/login`：登录入口，允许匿名。
3. `/payment/callback`：第三方回调，允许匿名，但必须签名/时间戳/幂等校验。

## 4.3 禁止项（冻结规则）

1. 禁止新增匿名写接口（POST/PUT/PATCH/DELETE）。
2. 新增匿名写接口必须走安全评审豁免，并至少具备：
   - 调用方身份校验（签名/HMAC 或同等级机制）
   - 防重放（timestamp + nonce 或等价机制）
   - 幂等与限流

---

## 5. 安全评审快速判定矩阵

| 问题 | 是 | 否 | 结论 |
|---|---|---|---|
| 是否写接口（POST/PUT/PATCH/DELETE） | 继续判定 | 非 CSRF 重点对象 | 读接口按越权评审 |
| 是否在匿名白名单中 | 校验签名/重放/幂等 | 必须 Header Token | 不允许“未鉴权写入” |
| 是否使用 cookie/session 自动凭证 | 必须启用 CSRF Token | 保持 Header JWT | 维持低风险边界 |
| 是否放开跨域敏感 Header + 凭证 | 高风险，禁止上线 | 可进入下一步 | 需 CORS 白名单收口 |

---

## 6. DoD 对齐（P3-S3）

- [x] 团队对 CSRF 边界与处置策略无歧义。  
- [x] 安全评审可按文档直接判定接口风险。  

---

## 7. 证据索引

1. `demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java`
2. `demo-service/src/main/resources/application.yml`
3. `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
4. `demo-service/src/main/java/com/demo/controller/admin/EmployeeController.java`
5. `demo-service/src/main/java/com/demo/controller/PaymentController.java`
6. `day18回归/执行记录/Day18_P3_S3_接口防伪边界验证结果_2026-02-25_14-59-09.json`

---

（文件结束）
