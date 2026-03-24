# UserFrontDay01 进度回填

- 日期：`2026-03-20`
- 文档版本：`v1.8`
- 当前状态：`进行中（真实 /login -> 首页摘要、真实 /logout 路由、手机注册真实提交、邮箱注册真实提交、邮箱激活手动 token POST、邮箱激活 query token 自动激活均已留证；首页摘要手动刷新点击仍待留痕）`
- 本轮范围：`只收口 UserFrontDay01 的“query token 自动激活”这个最小跨边界 blocker；不扩到 Day02+，不触碰 demo-admin-ui/docs/frontend-freeze/，也不把结论扩大成整站联调通过`

---

## 1. 本轮结论

本轮基于新的最小代码修复与最小运行复验，已把 Day01 的 `邮箱激活 query token 自动激活` 从“待验证 / blocker”升级为“运行态已确认”：

1. preview helper 暴露的真实 activationUrl 已从 backend 目标改为前端 `http://localhost:5173/activate/email?token=...`；
2. 直接打开该真实链接后，前端会自动触发 `GET /api/user/auth/register/email/activate?token=...`；
3. 接口返回 `200 / code=1 / msg=success`，页面成功提示可见，返回用户 `status=active`；
4. 因此，“query token 自动激活”这个 Day01 blocker 已最小收口，可以诚实回填到用户端 freeze；
5. 但 Day01 整体仍不能写成“已冻结完成”，因为首页 seller summary 手动刷新点击仍无独立浏览器留痕。

---

## 2. 本轮最小代码修复

### 2.1 实际改动文件

1. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
   - 必要性：把邮件激活链接从 backend JSON 接口改为前端 `/activate/email?token=...` 页面入口
2. `demo-service/src/main/java/com/demo/config/EmailProperties.java`
   - 必要性：把 `activationBaseUrl` 的默认值与语义修正为用户端前台 origin
3. `demo-service/src/main/resources/application.yml`
   - 必要性：让默认配置下的 activationBaseUrl 指向 `http://localhost:5173`
4. `demo-service/src/main/resources/application-dev.yml`
   - 必要性：修正 active=dev 运行态下对默认配置的覆盖，否则邮件链接仍会落回 backend origin

### 2.2 链接目标修正

- 之前真实链接指向：`http://localhost:8080/user/auth/register/email/activate?token=...`
- 现在应指向：`http://localhost:5173/activate/email?token=...`
- 为什么这是 Day01 最小正确收口：
  - 前端 `EmailActivatePage.vue` 里的 query token 自动激活入口原本就存在；
  - 真实问题在于邮件点击入口没有落到前端；
  - 所以本轮只修链接目标与配置覆盖，不改 token 校验协议，不做无关重构。

### 2.3 token 校验 / 消费逻辑说明

- 本轮没有修改 `GET /api/user/auth/register/email/activate?token=...` 的 token 校验规则；
- 失败点不在 token 校验实现本身，而在“真实邮件链接没有落到前端自动激活入口”；
- 修复后仍沿用既有 GET 激活消费逻辑，只让真实入口终于对齐到该逻辑。

---

## 3. 本轮最小复验

最小复验目录：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/userfront-day01-email-activate-query-auto-fix-minimal.json`

本轮已确认：

| 观察项 | 本轮结论 | 证据 |
|---|---|---|
| fresh email register | `pass` | `.../network/register-email-response.json` |
| preview helper activationUrl | `http://localhost:5173/activate/email?token=...` | `.../network/email-preview-latest-before-query-auto.json` |
| query token 自动激活请求 | `GET /api/user/auth/register/email/activate?token=... -> 200 / code=1 / msg=success` | `.../network/activate-email-query-response.json` |
| 页面结果 | 成功提示可见，最终 URL 保持在 `/activate/email?token=...` | `.../activate-email-query-result.txt`、`.../screenshots/activate-email-query-result.png` |
| 激活后用户状态 | 返回用户 `status=active`，用户 ID=`16` | `.../network/activate-email-query-response.json`、`.../userfront-day01-email-activate-query-auto-fix-minimal.json` |
| backend 留痕 | backend log 记录 activationUrl 已指向前端路由，且 `邮箱激活(GET)` 与 `邮箱激活完成，用户 ID=16` 均可对齐 | `.../backend-email-activate-query-excerpt.txt` |

保留观察：

- 浏览器控制台仍有 `404` 资源错误；
- 该观察当前不推翻“query token 自动激活已收口”的结论，但也不应被扩写成“Day01 其余入口都已无异常”。

---

## 4. 现在可以升级 / 不能升级的说法

| 项目 | 现在可以写成 | 绝对不能写成 |
|---|---|---|
| 邮箱激活 query token 自动激活 | `已留证` / `运行态已确认` / `Day01 该 blocker 已最小收口` | `Day01 已冻结完成` |
| 邮箱注册与激活子流 | `Day01 已完成并回填` | `整站联调已通过` |
| Day01 整体状态 | `进行中（仅剩首页 seller summary 手动刷新待补证）` | `整站登录 / 注册 / 激活全链路已通过` |

---

## 5. 当前剩余 Day01 缺口

1. 首页 seller summary `刷新摘要` 按钮仍只有代码绑定与旧文档口径，尚无单独浏览器留痕；
2. 因此，虽然 `query token 自动激活` blocker 已收口，但 Day01 仍不能诚实写成完成态。

---

## 6. 下一步建议

1. 这个 query token blocker 已可以退出 `drive-demo-user-ui-delivery`；
2. 若继续推进 Day01，下一步应转向首页 seller summary 手动刷新补证或 scope 调整；
3. 不要再把本线程扩大成整站登录/注册/激活全链路联调。
