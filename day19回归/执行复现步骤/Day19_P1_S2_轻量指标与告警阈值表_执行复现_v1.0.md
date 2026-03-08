# Day19 P1-S2 轻量指标与告警阈值表 执行复现 v1.0

- 日期：`2026-03-06`
- 关联规划文档：`day19回归/步骤规划/Day19_P1_S2_轻量指标与告警阈值表_v1.0.md`
- 目标：复现“阈值 -> 处置动作”闭环，并产出结构化执行证据

---

## 0. 先看结论（怎么用）

1. 先执行第 1 章前置检查（服务、数据库、管理员接口）。
2. 再执行第 2 章自动化脚本，生成 `Dynamic_Result_*.json`。
3. 如需手工核验，按第 3 章执行 SQL 与运维接口。
4. 最后按第 4 章 DoD 勾选，将结果回填执行记录文档。

---

## 1. 前置条件

1. 服务可访问：`http://localhost:8080`
2. MySQL 可访问：`localhost:3306/secondhand2`
3. 管理员账号可登录：`13900000001 / admin123`
4. 已存在目录：`day19回归/执行记录/`
5. 脚本可执行：`.tools/Day19_P1_S2_轻量指标与告警阈值校验.ps1`

---

## 2. 自动化执行（推荐）

## 2.1 快速模式（用于本地回归）

```powershell
powershell -ExecutionPolicy Bypass -File .\.tools\Day19_P1_S2_轻量指标与告警阈值校验.ps1 `
  -BaseUrl "http://localhost:8080" `
  -SampleRounds 3 `
  -SampleIntervalSeconds 10
```

## 2.2 标准模式（贴合 1 分钟观测窗口）

```powershell
powershell -ExecutionPolicy Bypass -File .\.tools\Day19_P1_S2_轻量指标与告警阈值校验.ps1 `
  -BaseUrl "http://localhost:8080" `
  -SampleRounds 3 `
  -SampleIntervalSeconds 60
```

## 2.3 带日志关键字统计

```powershell
powershell -ExecutionPolicy Bypass -File .\.tools\Day19_P1_S2_轻量指标与告警阈值校验.ps1 `
  -BaseUrl "http://localhost:8080" `
  -SampleRounds 3 `
  -SampleIntervalSeconds 10 `
  -LogFile "_tmp_day18_app18080.out.log"
```

执行成功后会输出：

1. `day19回归/执行记录/Day19_P1_S2_Dynamic_Result_yyyy-MM-dd_HH-mm-ss.json`
2. 告警级别汇总（每个告警ID的 `NORMAL/WARNING/ERROR/NO_SAMPLE`）
3. DoD 自动核对结果

---

## 3. 手工复现（接口 + SQL）

## 3.1 管理员登录

```powershell
$login = Invoke-RestMethod -Uri "http://localhost:8080/admin/employee/login" -Method Post -ContentType "application/json" -Body '{"loginId":"13900000001","password":"admin123"}'
$h = @{ token = $login.data.token }
```

## 3.2 Outbox 快照与处理

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/outbox/metrics" -Method Get -Headers $h
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/outbox/publish-once?limit=100" -Method Post -Headers $h
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/outbox/metrics" -Method Get -Headers $h
```

## 3.3 三类任务 run-once

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/ship-timeout/run-once?limit=200" -Method Post -Headers $h
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/refund/run-once?limit=200" -Method Post -Headers $h
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/ship-reminder/run-once?limit=200" -Method Post -Headers $h
```

## 3.4 SQL 快照（MySQL CLI）

```powershell
# Outbox 积压
mysql -h localhost -P 3306 -u root -p1234 -D secondhand2 -N -e "SELECT SUM(CASE WHEN status='NEW' THEN 1 ELSE 0 END) AS new_cnt,SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END) AS fail_cnt,SUM(CASE WHEN status IN ('NEW','FAIL') THEN 1 ELSE 0 END) AS backlog_cnt FROM message_outbox WHERE exchange_name NOT IN ('bad.exchange');"

# ship-timeout 可执行积压
mysql -h localhost -P 3306 -u root -p1234 -D secondhand2 -N -e "SELECT COUNT(1) FROM order_ship_timeout_task WHERE status='PENDING' AND deadline_time<=NOW() AND (next_retry_time IS NULL OR next_retry_time<=NOW());"

# refund 可执行积压
mysql -h localhost -P 3306 -u root -p1234 -D secondhand2 -N -e "SELECT COUNT(1) FROM order_refund_task WHERE status IN ('PENDING','FAILED') AND (next_retry_time IS NULL OR next_retry_time<=NOW());"

# ship-reminder 可执行积压
mysql -h localhost -P 3306 -u root -p1234 -D secondhand2 -N -e "SELECT COUNT(1) FROM order_ship_reminder_task WHERE status IN ('PENDING','FAILED') AND remind_time<=NOW();"
```

---

## 4. DoD 勾选

1. 每个阈值都有具体处置步骤  
- 验证方式：检查规划文档第 2 章每一条告警ID是否映射到 `FLOW-A/B/C`
2. 处置步骤可通过运维接口实操复现  
- 验证方式：至少成功执行一轮 `outbox publish-once + 三类 run-once`，且接口返回 `code=1`

---

## 5. 常见问题

1. 登录失败（401/403）  
- 先核对管理员账号与密码；再确认请求头使用 `token`。
2. MySQL 连接失败  
- 先确认本机 `mysql` 命令可用，再确认 `localhost:3306` 与账号密码。
3. `SR-OUTBOX/SR-TASK = NO_SAMPLE`  
- 表示本轮无处理样本（分母为 0），不是脚本错误。
4. 告警一直 `NORMAL`  
- 这是正常现象，表示当前系统无明显积压/失败放大；仍可用于证明处置流程可执行。

---

（文件结束）
