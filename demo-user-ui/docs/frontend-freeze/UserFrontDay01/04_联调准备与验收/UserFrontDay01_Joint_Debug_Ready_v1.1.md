# UserFrontDay01 联调准备与验收

- 日期：`2026-03-18`
- 文档版本：`v1.1`
- 当前状态：`进行中（已补 seller summary / auth header / real backend 401 的最小验收证据）`

---

## 1. 本轮联调范围

只覆盖 Day01 剩余的真实联调阻塞：

- `GET /user/seller/summary`
- 真实 backend `authentication` 请求头
- 真实 backend `401` 行为
- 首页错误态文案是否诚实反映 seller summary 失败

明确**不在本轮**扩展到：

- Day02+ 页面
- 整站联调
- 登录 / 注册 / 激活 / 退出的完整提交流程验收

---

## 2. 环境前置

1. backend 可访问：`http://localhost:8080`
2. 前端 dev 可访问：`http://localhost:5175`
3. `vite.config.ts` 的 `/api` 代理指向 `http://localhost:8080`
4. backend 用户端鉴权头固定为 `authentication`

---

## 3. 本轮保留的证据

| 类型 | 证据 |
|---|---|
| 构建 | `npm.cmd run build` pass |
| 直连接口真相 | 直接请求 `GET /user/seller/summary`：无头 / `Authorization` / 无效 `authentication` 均返回 `401`；有效 seller `authentication` 返回 `200 success` |
| 真实 backend 401 浏览器证据 | `demo-user-ui/.tmp_runtime/userfront-day01-real-401-dump.html` |
| seller summary 成功浏览器证据 | `demo-user-ui/.tmp_runtime/userfront-day01-real-summary-dump.html` |
| 非 seller 业务拒绝 + 首页失败态证据 | `demo-user-ui/.tmp_runtime/userfront-day01-non-seller-summary-dump.html` |

---

## 4. 本轮最小验收结论

| 检查项 | 结果 | 结论 |
|---|---|---|
| `authentication` 是否是真实后端要求的请求头 | pass | backend 与前端 request 层一致 |
| `Authorization` 是否可替代 `authentication` | fail | 不能替代，真实 backend 返回 401 |
| 无效 token 是否触发真实 backend 401 | pass | 不再只是 mock 401 结论 |
| 有效 seller token 是否能加载首页摘要 | pass | seller summary 可在真实 backend 下返回数据 |
| 非 seller token 是否出现业务拒绝而非 500 | pass | controller 走业务拒绝，不是稳定 500 |
| 从登录页真实提交拿到 seller token 后再进入首页 | not-run | 本轮没有补真实登录凭证链路 |

---

## 5. 当前仍保守描述的事实

- Day01 仍是 `进行中`；
- seller summary 不能直接写成“登录后全链路联调已通过”；
- 登录 / 注册 / 激活 / 退出依然需要独立运行证据；
- 本轮 seller summary 成功只证明 **真实 backend + 正确 header + 有效 seller 会话** 组合已可工作。
