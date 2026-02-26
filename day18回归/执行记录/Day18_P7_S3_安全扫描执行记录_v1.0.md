# Day18 P7-S3 安全扫描执行记录 v1.0

- 日期：2026-02-26
- 对应步骤：`Step P7-S3：安全扫描基线`
- 关联复现文档：`day18回归/执行复现步骤/Day18_P7_S3_安全扫描基线_执行复现_v1.0.md`

---

## 1. 执行命令

```powershell
docker run --rm -v "c:\Users\kk\Desktop\demo\tmp_zap:/zap/wrk" ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t "http://host.docker.internal:8080" -m 2 -r "Day18_P7_S3_zap_report.html" -J "Day18_P7_S3_zap_alerts.json" -w "Day18_P7_S3_zap_warnings.md"
```

执行结果：
1. 命令退出码为 `1`（存在告警，非执行失败）。
2. 报告文件生成成功。

---

## 2. 报告统计

来源：`day18回归/执行记录/Day18_P7_S3_zap_warnings.md`

1. High：0
2. Medium：0
3. Low：0
4. Informational：1

告警项：
1. `10049 Storable and Cacheable Content`（2 个实例）

---

## 3. 风险处置结论

1. 高危漏洞：无。
2. 中低风险：无。
3. Informational：
   - 对可能含敏感信息的响应补充 `Cache-Control` / `Pragma` / `Expires` 防缓存头。
   - 下一轮在回放更多业务流量后复扫，确认无新增中高风险。

---

## 4. 证据清单

1. `day18回归/执行记录/Day18_P7_S3_zap_alerts.json`
2. `day18回归/执行记录/Day18_P7_S3_zap_report.html`
3. `day18回归/执行记录/Day18_P7_S3_zap_warnings.md`

---

## 5. DoD 回填

| DoD 项 | 结果 | 说明 |
|---|---|---|
| 高危漏洞为 0 | `[x]` | ZAP 摘要 High=0 |
| 中低风险有明确处置计划 | `[x]` | 本次 Medium/Low 均为 0；Informational 已给出处置措施 |

---

（文件结束）
