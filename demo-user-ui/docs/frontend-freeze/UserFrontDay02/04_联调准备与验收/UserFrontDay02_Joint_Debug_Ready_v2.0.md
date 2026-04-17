# UserFrontDay02 联调准备与验收

- 日期：`2026-04-16`
- 文档版本：`v2.0`
- 当前状态：`进行中（修改密码子流最小运行态链路已在 2026-04-16 通过）`

---

## 1. 本轮验收范围（最小）

仅验证 Day02 子流：`账号安全 -> 修改密码` 的最小交付链路。

- 页面：`/account/security/password`
- API：`POST /user/me/password`
- 不扩展到：手机绑定/解绑、邮箱绑定/解绑

---

## 2. 本轮实际验证结果

| 验证项 | 结果 | 证据 |
|---|---|---|
| 前端构建 | pass | `demo-user-ui` 执行 `npm.cmd run build` 成功（2026-04-16） |
| 后端编译 | pass | 仓库根目录执行 `mvn -pl demo-pojo,demo-service -am -DskipTests compile` 成功（2026-04-16） |
| 运行态最小链路（当前密码路径） | pass | `2026-04-16` 实测：真实登录获取 token -> 成功进入 `/account/security/password` -> 点击提交触发 `POST /api/user/me/password` -> 返回业务成功（`code=1`）-> 完成“改密后改回”闭环 |

---

## 3. 2026-04-16 最小闭环证据路径

1. 真实登录成功并获得 token（`authentication` 会话已建立）。
2. 在登录态下访问并进入 `/account/security/password`。
3. 页面填写当前密码路径并点击提交，触发 `POST /api/user/me/password`。
4. 接口返回真实业务成功（`code=1`）。
5. 完成“改密后改回”闭环，确认账号可继续按原密码路径使用。

---

## 4. 历史 blocker 根因修正

- blocker 类型：`environment`
- 根因标签：`wrong-dev-instance-and-backend-not-listening`

---

## 5. 结论口径

- 本轮可声明：`代码已确认 + 构建已通过 + 运行态已确认（仅修改密码 current-password 最小链路）`
- 本轮不可声明：`Day02 已完成并回填`、`整站联调已通过`、`手机绑定/解绑与邮箱绑定/解绑运行态已确认`
