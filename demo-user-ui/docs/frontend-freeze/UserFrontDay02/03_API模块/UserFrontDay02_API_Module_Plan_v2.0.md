# UserFrontDay02 API 模块规划

- 日期：`2026-04-16`
- 文档版本：`v2.0`
- 当前状态：`进行中（新增“修改密码”子流代码闭环，运行态待验证）`

---

## 1. 本次只推进一个子流

Day02 子流：`账号安全 -> 修改密码`

---

## 2. 本次改动层与文件

| 层 | 文件 | 变更结论 |
|---|---|---|
| frontend page | `demo-user-ui/src/pages/AccountPasswordPage.vue` | 保持表单页职责，改为真实提交并更新边界文案 |
| frontend API | `demo-user-ui/src/api/security.ts` | 从“硬阻断”改为真实 `POST /user/me/password`，在 API 层完成字段归一化 |
| contract DTO | `demo-pojo/src/main/java/com/demo/dto/user/ChangePasswordRequest.java` | 新增 `currentPassword` 兼容字段 |
| backend service | `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java` | 密码校验从“仅 oldPassword”升级为“oldPassword 优先，currentPassword 回退” |
| backend controller | `demo-service/src/main/java/com/demo/controller/user/UserMeController.java` | `hasOldPassword` 统计逻辑兼容两种字段 |

---

## 3. API 边界决策

1. 页面不直接拼 `oldPassword/currentPassword`，统一下沉到 `src/api/security.ts`。
2. API 层在当前密码路径下同时发送 `oldPassword` 与 `currentPassword`，确保前后端过渡期兼容。
3. `confirmPassword` 只做前端一致性校验，不传后端。
4. `verifyChannel + code` 保留 contract 能力，本次不扩展页面交互。

---

## 4. 证据等级（本轮）

- 代码已确认：已完成
- 构建已通过：已完成
- 运行态已确认：未完成（环境未启动）

---

## 5. 与 Day02 当前状态的关系

本次仅新增“密码修改”子流的代码与构建级证据，不改变 Day02 “进行中”总判断，不得写成“Day02 已完成并回填”。
