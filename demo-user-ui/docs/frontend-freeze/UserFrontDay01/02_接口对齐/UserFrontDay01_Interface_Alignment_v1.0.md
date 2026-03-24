# UserFrontDay01 前后端接口对齐

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`进行中（骨架已建立，待执行验证）`

---

## 1. 对齐目标

围绕 `独立主工程基建与鉴权壳冻结`，固定 Day01 需要依赖的接口、字段、请求头和已知边界。

---

## 2. 接口清单

| 场景 | 接口 / 契约 | 当前口径 | 备注 |
|---|---|---|---|
| 密码登录 | `POST /user/auth/login/password` | 请求体 `{ loginId, password }`，成功后保存 `token + user` 到本地 session。 | 由 `src/api/auth.ts` + `src/utils/request.ts` + `src/pages/LoginPage.vue` 组成。 |
| 短信发送 | `POST /user/auth/sms/send` | 请求体 `{ mobile }`，仅服务于手机注册。 | 当前只冻结调用入口，不写成功率结论。 |
| 手机注册 | `POST /user/auth/register/phone` | 请求体 `{ mobile, smsCode, password, nickname }`。 | 成功后当前页面提示成功并回跳登录。 |
| 邮箱注册 | `POST /user/auth/register/email` | 请求体 `{ email, emailCode?, password, nickname }`。 | `emailCode` 当前前端参数可选，真实后端口径需以后续运行验证为准。 |
| 邮箱激活（GET） | `GET /user/auth/register/email/activate?token=...` | 支持 query token 自动激活。 | `EmailActivatePage.vue` 已接入。 |
| 邮箱激活（POST） | `POST /user/auth/register/email/activate` | 请求体 `{ token }`，支持手动输入 token 激活。 | 与 GET 激活共用同一页面。 |
| 邮件预览辅助 | `GET /user/auth/email-preview/latest` | 当前通过 `VITE_EMAIL_PREVIEW_PATH` 打开，用于开发期查看最新激活邮件。 | 仅作为辅助能力，不直接等同于业务闭环。 |
| 首页卖家摘要 | `GET /user/seller/summary` | 登录后首页拉取卖家摘要数据，失败时显示错误提示。 | 当前仅覆盖卖家摘要，不覆盖市场首页。 |
| Token 请求头 | 自定义请求头 `authentication` | 所有用户端受保护请求沿用统一请求拦截器注入。 | 不是 `Authorization: Bearer`。 |
| 未登录回跳 | 前端路由守卫 | 访问 `requiresAuth` 页面且本地无 token 时，重定向到 `/login?redirect=...`。 | 由 `src/router/index.ts` 控制。 |
| 401 清理 | 前端响应拦截器 | 若返回 401，则清理本地 session 并跳回登录页。 | 目前已有代码，待运行验证。 |

---

## 3. Day01 接口对齐原则

1. Auth / token / session / 401 行为统一放在 `src/utils/request.ts`，不分散到各页面。
2. 登录、注册、激活的接口路径统一通过 `src/api/auth.ts` 管理，页面只消费函数，不直接手写 URL。
3. 首页卖家摘要统一通过 `src/api/seller.ts`，不把 seller summary 请求塞进 auth 模块。
4. `authentication` 是当前固定协议；在代码和后端都没变之前，文档不得写成 `Authorization: Bearer`。
5. 账户中心当前只读取本地 session，不在 Day01 虚构不存在的 profile 刷新接口。

---

## 4. 已知缺口 / 边界说明

| 项目 | 当前情况 | 文档口径 |
|---|---|---|
| 用户 profile 刷新接口 | 当前前端未接入独立 profile 查询或更新接口 | 账户中心只记为“基础展示进行中”，不写成完整资料中心。 |
| 邮件预览辅助接口 | 当前可打开最新邮件预览 | 只作为开发辅助证据，不等于真实产品收口。 |
| 地址 / 收藏 / 市场 / 订单 / 钱包 / 积分 | 后端接口存在，但前端页面与 API 模块未建立 | 保留在覆盖矩阵，延后到对应 `UserFrontDay`。 |
| 运行态结论 | 当前仅确认代码面与构建面 | 未实测的链路只能写 `待验证`。 |

---

## 5. 与后续接口日的衔接

- Day02 以后若新增 `address.ts`、`profile.ts` 等模块，必须先在对应日文档里冻结口径，再写代码。
- Day03 以后若接 `market`、`favorite` 等接口，必须把列表、详情、举报等子流拆开记账。
- Day05 ~ Day07 进入订单、钱包、积分时，必须先确认状态机、失败态和风险说明，不得只记接口路径。
