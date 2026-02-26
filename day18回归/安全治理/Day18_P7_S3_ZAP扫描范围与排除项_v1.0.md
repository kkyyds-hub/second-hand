# Day18 P7-S3 ZAP 扫描范围与排除项 v1.0

- 日期：2026-02-26
- 对应步骤：`Step P7-S3：安全扫描基线`
- 目的：定义可重复执行的 OWASP ZAP 基线扫描边界，避免误扫生产危险接口。

---

## 1. 扫描目标与原则

1. 扫描目标：`http://host.docker.internal:8080`（容器访问宿主机服务）。
2. 扫描类型：`zap-baseline.py`（被动扫描，不做主动攻击）。
3. 原则：
   - 先建立最小可重复闭环（命令可执行、报告可留存）。
   - Day18 仅做基线与风险分级，不在本步骤引入破坏性扫描。

---

## 2. 范围定义（In Scope）

1. 服务根入口与可被被动发现的公开 GET 资源。
2. 框架/网关默认返回页面与错误页（用于响应头与缓存策略检查）。
3. 扫描输出：
   - HTML 报告
   - JSON 告警明细
   - Markdown 摘要

---

## 3. 排除项定义（Out of Scope）

1. 所有写操作接口（`POST/PUT/DELETE/PATCH`）不纳入 ZAP Active Scan。
2. 运维高风险接口（建议后续都加入排除）：
   - `/admin/ops/**`
   - `/admin/orders/*/ship`
   - `/admin/orders/*/cancel`
   - `/admin/user/*/ban`
   - `/admin/user/*/unban`
3. 依赖业务数据状态的回放接口（支付回调、任务 run-once）不做自动化主动探测。

说明：
- 本次执行使用 baseline，被动扫描天然不会发起攻击性 payload。
- 上述排除项用于后续升级到主动扫描时的强制边界。

---

## 4. 风险分级与处置门槛

1. `High`：必须立即阻断上线，24 小时内给出修复或缓解方案。
2. `Medium`：需在迭代内完成修复并附带复测证据。
3. `Low`：进入安全债务清单，给出版本计划。
4. `Informational`：按“是否涉及敏感数据泄露”决定是否整改。

Day18 冻结门槛：
1. `High = 0`。
2. `Medium/Low` 必须有明确处置计划或风险接受说明。

---

## 5. 标准执行命令（Docker）

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
New-Item -ItemType Directory -Force -Path 'tmp_zap' | Out-Null

docker run --rm `
  -v "c:\Users\kk\Desktop\demo\tmp_zap:/zap/wrk" `
  ghcr.io/zaproxy/zaproxy:stable `
  zap-baseline.py `
  -t "http://host.docker.internal:8080" `
  -m 2 `
  -r "Day18_P7_S3_zap_report.html" `
  -J "Day18_P7_S3_zap_alerts.json" `
  -w "Day18_P7_S3_zap_warnings.md"
```

退出码说明：
1. `0`：无告警。
2. `1`：有告警（常见为 Informational/Warning），需结合报告判定。
3. `2` 及以上：执行失败或配置异常。

---

## 6. 本次执行边界备注

1. 当前应用根路径返回 `404`，ZAP 可发现端点数量较少（2 个）。
2. 该结果可用于建立“安全扫描最小闭环”，但不能替代深度渗透测试。
3. 后续建议：通过代理回放 Day18 回归流量，再进行被动规则复扫，扩大覆盖面。

---

（文件结束）
