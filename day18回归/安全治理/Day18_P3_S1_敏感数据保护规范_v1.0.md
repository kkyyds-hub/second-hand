# Day18 P3-S1 敏感数据保护规范 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P3-S1：敏感数据保护收口`
- 目标：确保密码、令牌、密钥与验证码等敏感信息在存储、传输、日志中的风险可控。

---

## 1. 适用范围

1. 认证与鉴权链路（登录、注册、激活、Token 校验）。
2. 账号安全链路（改密、绑定/解绑手机与邮箱）。
3. 配置与密钥治理（`application.yml` / `application-dev.yml`）。
4. 个人信息链路（地址、订单收货信息日志输出）。

---

## 2. 敏感数据分级与处理要求

| 类型 | 示例 | 存储要求 | 日志要求 | 传输要求 |
|---|---|---|---|---|
| 一级机密 | 密码、JWT 密钥、第三方 secret | 不可明文持久化（密码必须哈希；密钥走环境变量） | 禁止输出明文/完整值 | 仅 HTTPS/受控内网 |
| 二级敏感 | JWT token、验证码、激活 token、授权码 | 仅短期缓存（TTL）或一次性使用 | 仅允许脱敏/长度信息 | 请求体/请求头传输，禁止日志明文 |
| 三级隐私 | 手机号、邮箱、详细地址 | 业务必需最小化存储 | 禁止整对象日志，允许脱敏输出 | 仅在必要接口返回 |

---

## 3. 代码收口结果（P3-S1）

## 3.1 日志脱敏收口

1. JWT 拦截器不再打印完整 token，仅打印脱敏值：
   - `JwtTokenUserInterceptor`
   - `JwtTokenAdminInterceptor`
2. 认证服务不再打印明文验证码与激活 token：
   - `AuthServiceImpl.sendSmsCode` 仅记录脱敏手机号
   - `AuthServiceImpl.sendActivationMail` 仅记录脱敏邮箱
3. 认证控制器不再打印完整请求对象（避免密码/验证码/token 泄露）：
   - `UserAuthController`
4. 账号安全控制器不再打印改密/绑定/解绑请求明文：
   - `UserMeController`
5. 地址与订单创建日志去除整包 PII 输出：
   - `AddressController`、`AddressServiceImpl`、`OrdersController`

## 3.2 配置分层与密钥治理

1. JWT 密钥改为环境变量优先：
   - `demo.jwt.admin-secret-key: ${DEMO_JWT_ADMIN_SECRET:...}`
   - `demo.jwt.user-secret-key: ${DEMO_JWT_USER_SECRET:...}`
2. 开发环境可使用默认值联调，生产环境必须显式注入环境变量并禁用默认弱口令。
3. 禁止在生产配置中提交明文密钥、数据库密码、第三方 API Key。

---

## 4. 高风险操作二次验证策略（冻结）

| 操作 | 接口 | 二次验证策略 | 当前实现状态 |
|---|---|---|---|
| 修改密码 | `POST /user/me/password` | `oldPassword` 或 `verifyChannel+code` 至少一种 | 已实现 |
| 绑定手机 | `POST /user/me/bindings/phone` | 必须验证新手机号验证码 | 已实现 |
| 绑定邮箱 | `POST /user/me/bindings/email` | 必须验证新邮箱验证码 | 已实现 |
| 解绑手机 | `DELETE /user/me/bindings/phone` | `currentPassword` 或 `verifyChannel+verifyCode` | 已实现 |
| 解绑邮箱 | `DELETE /user/me/bindings/email` | `currentPassword` 或 `verifyChannel+verifyCode` | 已实现 |

---

## 5. 配置分层要求（开发/生产）

## 5.1 开发环境（`application-dev.yml`）

1. 允许本地测试账号与本地中间件配置。
2. 不得使用生产密钥/生产账号。
3. 测试密钥定期轮换，禁止跨团队共享。

## 5.2 生产环境（强制）

1. 所有密钥通过环境变量或密钥管理服务注入，不落盘到仓库。
2. 配置审计必须包含：JWT 密钥、数据库密码、MQ 密码、邮件凭据、第三方 API key。
3. 发布前执行一次敏感日志扫描（检查明文 `password/token/code/secret`）。

---

## 6. DoD 对齐（P3-S1）

- [x] 不在日志中泄露明文敏感字段（代码扫描与改造完成）。  
- [x] 高风险操作具备二次验证路径（动态验证已执行）。  
- [x] 已形成配置分层与密钥治理规范。  

---

## 7. 代码证据索引

1. `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`
2. `demo-service/src/main/java/com/demo/interceptor/JwtTokenAdminInterceptor.java`
3. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
4. `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
5. `demo-service/src/main/java/com/demo/controller/user/UserMeController.java`
6. `demo-service/src/main/java/com/demo/controller/user/AddressController.java`
7. `demo-service/src/main/java/com/demo/service/serviceimpl/AddressServiceImpl.java`
8. `demo-service/src/main/java/com/demo/controller/user/OrdersController.java`
9. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
10. `demo-service/src/main/resources/application.yml`

---

（文件结束）
