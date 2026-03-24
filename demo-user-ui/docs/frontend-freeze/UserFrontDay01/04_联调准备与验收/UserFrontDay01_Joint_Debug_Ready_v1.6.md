# UserFrontDay01 联调准备与验收

- 日期：`2026-03-21`
- 文档版本：`v1.6`
- 当前状态：`已完成并回填（首页 seller summary 手动刷新点击最小验收已通过，Day01 最后剩余尾项已收口）`

---

## 1. 本轮联调范围

本轮不新增运行验证，只回填一个 Day01 最后尾项的既有运行态证据：

- in scope：首页 seller summary 自动加载后的 `刷新摘要` 点击、点击后二次真实 `GET /api/user/seller/summary`、页面继续保持“卖家摘要已加载，可手动刷新。”状态
- out of scope：Day02+、新的代码改动、backend controller 改动、admin 文档、整站联调结论

---

## 2. 本轮采用证据

- 主证据目录：`demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/`
- 复用构建证据：`demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/frontend-build.log`
- 本次文档回填仅引用既有 `summary.md`、machine summary、network、screenshots、result dump，不在本线程再次发起验证。

---

## 3. 最小验收口径

只有同时满足以下条件，才把首页 seller summary `手动刷新点击` 记为 `pass`：

1. 既有真实登录 setup 能进入首页 `/`；
2. 自动加载阶段已出现一次真实 seller summary 成功响应；
3. 点击前 `刷新摘要` 按钮可见且可点击；
4. 点击后浏览器观察到第二次真实 `GET /api/user/seller/summary` 请求；
5. 该请求返回 `200 / code=1 / msg=success`；
6. 点击后摘要状态仍显示“卖家摘要已加载，可手动刷新。”，主要统计卡片继续可见，且页面无错误 banner；
7. 结论只覆盖 Day01 首页摘要最小链路，不外推成完整首页或整站联调通过。

---

## 4. 本轮结果

| 环节 | 结果 | 证据 |
|---|---|---|
| 复用 frontend build evidence | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/frontend-build.log` |
| 真实登录 setup 进入首页 | `pass` | `.../network/login-password-response.json`、`.../userfront-day01-home-seller-summary-manual-refresh-minimal.json` |
| 首页摘要自动加载 | `pass` | `.../network/seller-summary-auto-load-response.json`、`.../screenshots/home-seller-summary-auto-loaded.png` |
| `刷新摘要` 按钮点击已发生 | `pass` | `.../userfront-day01-home-seller-summary-manual-refresh-minimal.json` |
| 点击后二次真实 `GET /api/user/seller/summary` | `pass` | `.../network/seller-summary-manual-refresh-request.json` |
| 手动刷新响应 | `pass` | `.../network/seller-summary-manual-refresh-response.json` |
| 点击后页面状态与统计卡片 | `pass` | `.../home-seller-summary-manual-refresh-result.txt`、`.../screenshots/home-seller-summary-manual-refresh-after-click.png` |

---

## 5. 保留观察

- machine summary 记录的手动刷新点击时间为 `2026-03-21T03:37:31.911Z`，请求完成时间为 `2026-03-21T03:37:31.966Z`；
- 控制台仍有 `404` 资源错误；
- `pageErrors=[]`、`failedRequests=[]`；
- 这些观察当前不推翻“首页 seller summary 手动刷新点击已通过最小验收”的结论，但也不应被解释成“整站其余入口均无异常”。

---

## 6. 本轮关键证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/userfront-day01-home-seller-summary-manual-refresh-minimal.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/network/login-password-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/network/seller-summary-auto-load-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/network/seller-summary-manual-refresh-request.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/network/seller-summary-manual-refresh-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-21/userfront-day01-home-seller-summary-manual-refresh-minimal/home-seller-summary-manual-refresh-result.txt`

---

## 7. 本轮结论边界

本轮结论只能写成：

- Day01 首页 seller summary 手动刷新点击 `运行态已确认`；
- Day01 首页摘要最小链路现已 `已完成并回填`；
- `UserFrontDay01` 现在可以正式收口。

本轮结论绝对不能写成：

- `整站联调已通过`；
- `Day02+ 已覆盖`；
- `所有未来回归都已通过`。
