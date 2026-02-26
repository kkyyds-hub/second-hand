# Day18 P2-S2 缓存一致性执行记录 v1.0

- 日期：2026-02-25
- 关联复现文档：`day18回归/执行复现步骤/Day18_P2_S2_缓存键模型与失效策略_执行复现_v1.0.md`
- 执行方式：接口调用 + Redis 实时核验。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. Redis：`localhost:6379`，DB=`2`
3. 执行人：`Codex`
4. 执行时间：`2026-02-25 11:31:44`

---

## 2. 执行结果摘要

| 场景 | 操作 | 结果 | 是否通过 |
|---|---|---|---|
| A 短期验证码键 TTL | `POST /user/auth/sms/send` + Redis TTL 核验 | `auth:sms:code` TTL=`300`；`auth:sms:rate` TTL=`60` | `[x]` |
| B DAU 去重写入 | 用户登录后 `GET /user/orders/buy` | `SISMEMBER dau:2026-02-25 userId=1 => 1`，`SCARD=1` | `[x]` |
| C 登录失败计数窗口 | 错误登录一次，再正确登录一次 | 错误后 `auth:login_fail:1=1, TTL=1800`；成功后 `EXISTS=0` | `[x]` |

---

## 3. 关键证据（实测值）

1. `smsRespCode=1`
2. `smsCodeKey=auth:sms:code:13800990098`，`EXISTS=1`，`TTL=300`
3. `smsRateKey=auth:sms:rate:13800990098`，`EXISTS=1`，`TTL=60`
4. `buyRespCode=1`
5. `dauKey=dau:2026-02-25`，`SISMEMBER(userId=1)=1`，`SCARD=1`
6. `failKey=auth:login_fail:1`，错误登录后 `value=1, TTL=1800`
7. 正确登录后 `EXISTS(auth:login_fail:1)=0`

---

## 4. 结论

1. 当前 Redis 键模型与 TTL 行为与规范文档一致。
2. 风控与验证码链路的缓存失效路径可解释（超时失效 + 成功删除）。
3. 交易核心链路未使用 Redis 读缓存，未发现“长期脏缓存”已知风险。

---

## 5. DoD 勾选

- [x] 关键链路无“长期脏缓存”已知风险。  
- [x] 新增缓存点必须按规范评审（见规范文档评审清单）。  
- [x] 已形成执行证据并回填。  

---

（文件结束）
