# Day18 P3-S1 敏感数据保护执行记录 v1.0

- 日期：2026-02-25
- 关联复现文档：`day18回归/执行复现步骤/Day18_P3_S1_敏感数据脱敏与二次验证_执行复现_v1.0.md`
- 当前状态：已执行并完成回填。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. 执行人：`Codex`
3. 执行时间：`2026-02-25 13:55:24 ~ 13:55:30`
4. 原始证据：`day18回归/执行记录/Day18_P3_S1_二次验证动态结果_2026-02-25_13-55-24.json`

---

## 2. 场景执行结果

| 场景 | 操作 | 预期 | 实际结果 | 是否通过 |
|---|---|---|---|---|
| A1 敏感日志扫描 | `rg` 扫描 token/验证码日志 | 不出现明文密码/验证码/完整 token | 仅保留脱敏 token 或长度信息，无明文验证码/激活 token | `[x]` |
| A2 request 整包扫描 | `rg` 扫描 `request` 整包日志 | 认证与账号安全链路不打印整包 request | 未检出相关高风险整包日志 | `[x]` |
| B 改密二次验证 | `POST /user/me/password` 仅传 `newPassword` | 拒绝执行 | 返回 `code=0`，提示需旧密码或验证码 | `[x]` |
| C1 解绑二次验证 | `DELETE /user/me/bindings/phone` 空请求 | 拒绝执行 | 返回 `code=0`，验证码不能为空 | `[x]` |
| C2 绑定手机验证码 | `POST /user/me/bindings/phone` 错误验证码 | 拒绝执行 | 返回 `code=0`，验证码错误或已过期 | `[x]` |
| C3 绑定邮箱验证码 | `POST /user/me/bindings/email` 错误验证码 | 拒绝执行 | 返回 `code=0`，验证码错误或已过期 | `[x]` |

---

## 3. 关键结果摘录

1. 二次验证动态结果文件显示：
   - `changePassword_without_second_verify.code = 0`
   - `unbindPhone_without_second_verify.code = 0`
   - `bindPhone_with_invalid_code.code = 0`
   - `bindEmail_with_invalid_code.code = 0`
2. 代码扫描显示：
   - JWT 日志改为 `maskToken(...)`
   - 验证码/激活 token 不再明文输出
   - 认证与账号安全控制器日志不再打印敏感请求对象

---

## 4. DoD 勾选（回填区）

- [x] 不在日志中泄露明文敏感字段。  
- [x] 高风险操作具备二次验证路径。  
- [x] 已形成执行证据（扫描命令 + 动态返回 + 原始结果文件）。  

---

（文件结束）
