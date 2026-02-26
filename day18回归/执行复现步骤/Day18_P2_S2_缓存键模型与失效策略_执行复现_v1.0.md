# Day18 P2-S2 缓存键模型与失效策略 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证 Redis 键模型、TTL 与关键失效行为符合规范文档。

---

## 1. 前置条件

1. 服务已启动（`http://localhost:8080`）。
2. Redis 已启动且可访问（开发环境 DB=2）。
3. 已准备测试账号（示例：`13800000001/123456`）。

---

## 2. 场景 A：短信验证码键 TTL

1. 调用：
   - `POST /user/auth/sms/send`
   - body: `{"mobile":"13800990098"}`
2. 读取 Redis：
   - `EXISTS auth:sms:code:13800990098`
   - `TTL auth:sms:code:13800990098`
   - `EXISTS auth:sms:rate:13800990098`
   - `TTL auth:sms:rate:13800990098`
3. 预期：
   - code 键存在且 TTL 约 300 秒；
   - rate 键存在且 TTL 约 60 秒。

---

## 3. 场景 B：DAU 去重写入

1. 用户登录：
   - `POST /user/auth/login/password`
2. 使用用户 token 调用任意 `GET /user/**`（示例：`GET /user/orders/buy?page=1&pageSize=1`）。
3. Redis 核验：
   - `SISMEMBER dau:{today} {userId}`
   - `SCARD dau:{today}`
4. 预期：
   - `SISMEMBER=1`；
   - `SCARD` 为正整数。

---

## 4. 场景 C：登录失败计数与成功清理

1. 对同一账号执行一次错误密码登录。
2. Redis 核验：
   - `GET auth:login_fail:{userId}`
   - `TTL auth:login_fail:{userId}`
3. 再执行一次正确密码登录。
4. Redis 核验：
   - `EXISTS auth:login_fail:{userId}`
5. 预期：
   - 错误登录后计数为 `1`，TTL 约 1800 秒；
   - 正确登录后计数键被删除（`EXISTS=0`）。

---

## 5. 推荐命令（PowerShell）

```powershell
# Redis 基础
D:\redis\redis-cli.exe -n 2 PING

# 场景 A
D:\redis\redis-cli.exe -n 2 EXISTS auth:sms:code:13800990098
D:\redis\redis-cli.exe -n 2 TTL auth:sms:code:13800990098
D:\redis\redis-cli.exe -n 2 EXISTS auth:sms:rate:13800990098
D:\redis\redis-cli.exe -n 2 TTL auth:sms:rate:13800990098

# 场景 B
D:\redis\redis-cli.exe -n 2 SISMEMBER dau:2026-02-25 1
D:\redis\redis-cli.exe -n 2 SCARD dau:2026-02-25

# 场景 C
D:\redis\redis-cli.exe -n 2 GET auth:login_fail:1
D:\redis\redis-cli.exe -n 2 TTL auth:login_fail:1
D:\redis\redis-cli.exe -n 2 EXISTS auth:login_fail:1
```

---

## 6. DoD 勾选

- [ ] 关键链路无“长期脏缓存”已知风险。  
- [ ] 新增缓存点必须按规范评审。  
- [ ] 执行记录已回填。  

---

（文件结束）
