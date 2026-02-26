# Day18 P5-S2 登录风控与账号解冻 执行复现 v1.0

- 日期：2026-02-25
- 目标：复现“连续失败触发冻结 + 人工解冻恢复 + 审计日志可检索”。

---

## 1. 前置条件

1. 服务已启动并可访问：`http://localhost:8080`。
2. 测试库可访问（示例：`secondhand2`）。
3. 已准备账号：
   - 管理员账号（用于解冻）
   - 普通用户账号（作为风控触发目标）
4. 目标用户初始状态为 `active`。

---

## 2. 场景 A：连续失败触发冻结

1. 对同一账号连续发送 5 次错误密码登录：
   - `POST /user/auth/login/password`
2. 预期：
   - 第 1~5 次返回“用户名或密码错误”；
   - 第 5 次后账号状态已被置为 `frozen`。
3. 第 6 次（错误或正确密码都可）再调用登录。
4. 预期：返回“账号已被暂时冻结，请稍后再试或联系管理员”。

---

## 3. 场景 B：人工解冻

1. 管理员调用：`PUT /admin/user/{userId}/unban`。
2. 预期：
   - 首次返回“用户解封成功”；
   - 重复调用返回“用户已处于正常状态”（幂等）。

---

## 4. 场景 C：解冻后登录恢复

1. 使用目标账号正确密码重新登录。
2. 预期：登录成功并返回 token。

---

## 5. 场景 D：审计日志字段核验

1. 在 IDEA 控制台按 `AUDIT` 过滤日志。
2. 重点检索：
   - `action=USER_LOGIN`
   - `action=LOGIN_RISK_FREEZE`
   - `action=USER_UNBAN`
3. 预期：
   - `LOGIN_RISK_FREEZE` 至少出现 1 条；
   - 字段包含 `auditId/action/actorType/actorId/targetType/targetId/result/ip/detail`；
   - `detail` 包含 `failCount`，成功场景包含 `banId`。

---

## 6. SQL 核验（示例）

```sql
-- 用户状态核验
SELECT id, status, update_time
FROM users
WHERE id = ?;

-- 自动风控 ban 记录核验（最新一条）
SELECT id, user_id, ban_type, source, reason, start_time, end_time, create_time
FROM user_bans
WHERE user_id = ?
ORDER BY id DESC
LIMIT 1;

-- 信用快照核验（冻结后通常会受 BAN_ACTIVE 影响）
SELECT id, credit_score, credit_level, credit_updated_at
FROM users
WHERE id = ?;
```

---

## 7. DoD 勾选

- [ ] 连续失败触发保护逻辑可复现。  
- [ ] 解冻流程有明确操作规范并可执行。  
- [ ] 审计字段核验与执行记录已回填。  

---

（文件结束）
