# Day18 P7-S3 安全测试报告 v1.0

- 日期：2026-02-26
- 对应步骤：`Step P7-S3：安全扫描基线`
- 扫描工具：OWASP ZAP (`ghcr.io/zaproxy/zaproxy:stable`, ZAP 2.17.0)
- 扫描方式：`zap-baseline.py`（被动扫描）
- 扫描目标：`http://host.docker.internal:8080`

---

## 1. 执行摘要

1. 扫描命令已成功执行并输出报告。
2. 扫描告警统计：
   - High：0
   - Medium：0
   - Low：0
   - Informational：1
3. 结论：满足 Day18 `高危漏洞为 0` 的冻结门槛。

---

## 2. 告警明细与处置

| 告警项 | 风险级别 | 实例数 | 处置策略 | 当前状态 |
|---|---|---:|---|---|
| Storable and Cacheable Content (`10049`) | Informational | 2 | 若返回敏感内容，补 `Cache-Control: no-store, no-cache, private`；否则记录为可接受风险 | 已记录，待 P8 统一评审 |

说明：
1. 本次未发现 High/Medium/Low 风险。
2. Informational 项不阻断 Day18 冻结，但已给出处置方向。

---

## 3. 证据文件

1. `day18回归/执行记录/Day18_P7_S3_zap_alerts.json`
2. `day18回归/执行记录/Day18_P7_S3_zap_report.html`
3. `day18回归/执行记录/Day18_P7_S3_zap_warnings.md`
4. `day18回归/执行记录/Day18_P7_S3_安全扫描执行记录_v1.0.md`

---

## 4. 覆盖范围与局限

1. 当前基线扫描已形成“可重复执行最小闭环”。
2. 受目标站点可爬取入口限制（根路径 404），本次自动发现端点数较少。
3. 下一步建议：
   - 用 ZAP 代理回放 Day18 回归集合，扩大被动扫描覆盖。
   - 对 cookie/session 场景单独补 CSRF 专项扫描。

---

## 5. DoD 对齐

- [x] 高危漏洞为 0。  
- [x] 中低风险有明确处置计划。  
说明：本次无中低风险；Informational 告警已提供处置方案与评审动作。

---

（文件结束）
