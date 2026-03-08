# Day18 P5-S2 登录风控执行记录 v1.0

- 日期：2026-02-26（复验） + 2026-03-04（审计字段补证）
- 关联复现文档：`day18回归/执行复现步骤/Day18_P5_S2_登录风控与账号解冻_执行复现_v1.0.md`
- 当前状态：已完成（A/B/C/D 均已回填，审计字段检索补证已闭环）。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`（历史复验） / `http://localhost:8080`（2026-03-04 补证）
2. 数据库：`secondhand2`
3. 执行人：`Codex`
4. 执行时间：`2026-02-26 00:20:03 ~ 00:20:04` + `2026-03-04 11:52`
5. 原始证据：
   - `day18回归/执行记录/Day18_P5_S2_动态验证结果_2026-02-26_00-20-03.json`
   - `day18回归/执行记录/Day18_P5_S2_Audit_CloseLoop_Result_2026-03-04_11-52-17.json`

---

## 2. 静态核验结论（已完成）

1. 登录失败阈值与窗口口径已固定：`5 次 / 30 分钟`。
2. 自动冻结采用 CAS：`active -> frozen`。
3. 自动冻结会写 `user_bans(source=AUTO_RISK)` 并触发信用重算。
4. 已补充 `LOGIN_RISK_FREEZE` 审计字段并与 `USER_LOGIN` 串联。

---

## 3. 动态执行回填表

| 场景 | 输入 | 预期 | 实际结果 | 是否通过 |
|---|---|---|---|---|
| A 连续失败触发冻结 | 同账号错误密码 5 次 + 第 6 次登录 | 第 6 次返回“账号已被冻结” | 第 1~5 次返回“用户名或密码错误”；第 6 次与后续正确密码均返回“账号已被暂时冻结…”；SQL `users.id=2` 变更为 `frozen`；`AUTO_RISK` 记录数 `3 -> 4` | `[x]` |
| B 管理员解冻 | `PUT /admin/user/{userId}/unban` | 首次成功，重复幂等 | 第一次返回“用户解封成功”；第二次返回“用户已处于正常状态”；SQL `users.id=2` 恢复为 `active` | `[x]` |
| C 解冻后恢复登录 | 正确密码登录 | 返回 token | `POST /user/auth/login/password` 返回 `code=1`，成功签发 token | `[x]` |
| D 审计字段核验 | 日志检索 `USER_LOGIN`/`LOGIN_RISK_FREEZE`/`USER_UNBAN` | 字段齐全、可串联 | 2026-03-04 运行 `Day18_P5_S2_审计闭环校验.ps1` 补证：`closed_loop_pass=true`，三类 action 均命中，字段完整（含 `auditId/action/actorType/actorId/targetType/targetId/result/ip/detail`）；`LOGIN_RISK_FREEZE` 样本含 `failCount=5,source=AUTO_RISK,banId` | `[x]` |

---

## 4. 关键结果摘录

1. 冻结前基线：`users.id=2 status=active`，`AUTO_RISK` 封禁记录计数=3。  
2. 连续错密后：第 6 次登录返回冻结提示；`users.id=2 status=frozen`；新增 `user_bans` 记录（`source=AUTO_RISK`,`ban_type=TEMP`，`end_time=+1h`），计数 `3 -> 4`。  
3. 人工解冻后：首次解冻成功、重复解冻幂等命中；`users.id=2 status=active`。  
4. 解冻后正确密码登录成功并返回 token。  
5. 审计字段补证：已在日志中检索到 `USER_LOGIN` / `LOGIN_RISK_FREEZE` / `USER_UNBAN`，并归档结构化证据文件。  

---

## 5. DoD 勾选（回填区）

- [x] 连续失败触发保护逻辑可复现。  
- [x] 解冻流程有明确操作规范。  
- [x] 审计字段核验完成并可检索（已补结构化日志证据）。  

---

（文件结束）
