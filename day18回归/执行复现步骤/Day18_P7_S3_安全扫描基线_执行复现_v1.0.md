# Day18 P7-S3 安全扫描基线 执行复现 v1.0

- 日期：2026-02-26
- 目标：复现 OWASP ZAP 基线扫描并产出可归档报告。

---

## 1. 前置条件

1. 目标服务已启动（本地：`localhost:8080`）。
2. Docker Desktop 运行正常。
3. 可访问镜像：`ghcr.io/zaproxy/zaproxy:stable`。

---

## 2. 执行步骤

1. 创建输出目录：

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
New-Item -ItemType Directory -Force -Path 'tmp_zap' | Out-Null
```

2. 运行 ZAP baseline：

```powershell
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

3. 归档报告到 Day18 执行记录目录：

```powershell
Copy-Item -Force 'tmp_zap\Day18_P7_S3_zap_alerts.json' 'day18回归\执行记录\Day18_P7_S3_zap_alerts.json'
Copy-Item -Force 'tmp_zap\Day18_P7_S3_zap_report.html' 'day18回归\执行记录\Day18_P7_S3_zap_report.html'
Copy-Item -Force 'tmp_zap\Day18_P7_S3_zap_warnings.md' 'day18回归\执行记录\Day18_P7_S3_zap_warnings.md'
```

---

## 3. 预期结果

1. 生成 3 个报告文件（JSON/HTML/MD）。
2. `High` 告警为 0。
3. 若存在 Medium/Low/Informational，需在执行记录中附处置计划。

---

## 4. DoD 勾选

- [ ] 高危漏洞为 0。  
- [ ] 中低风险有明确处置计划。  

---

（文件结束）
