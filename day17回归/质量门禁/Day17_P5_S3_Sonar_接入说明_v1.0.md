# Day17 P5-S3 Sonar 接入说明 v1.0

- 日期：2026-02-24
- 目的：提供可直接复用的 SonarQube 扫描口径，确保团队可复现。

---

## 1. 前置条件

1. 已有 SonarQube 服务地址（如 `http://localhost:9000`）。  
2. 已创建 Token（建议使用项目级 Token）。  
3. 本机可执行 Maven（`mvn -v` 正常）。  
4. 项目根目录：`c:\Users\kk\Desktop\demo`。

---

## 2. 推荐扫描命令（Maven）

```bash
mvn -DskipTests clean verify sonar:sonar \
  -Dsonar.projectKey=demo-platform-day17 \
  -Dsonar.projectName=demo-platform-day17 \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=替换为你的token \
  -Dsonar.sourceEncoding=UTF-8
```

---

## 3. 建议质量阈值（Quality Gate）

1. `Blocker Issues = 0`  
2. `Critical Issues = 0`（至少 Day17 改造范围内）  
3. `Security Hotspots Reviewed = 100%`  
4. `Duplicated Lines (%)` 不高于团队阈值（建议 `< 5%`）  
5. `Coverage` 对 Day17 关键模块达到团队基线（结合 P5-S1 测试）

---

## 4. 扫描口径说明

1. Day17 验收以改造范围优先（service/job/mapper/关键 controller）。  
2. 历史遗留告警可登记为技术债，但不得掩盖 Day17 新引入问题。  
3. Sonar 结果需与 `Day17_P5_S3_问题处置清单_v1.0.md` 对齐。

---

## 5. 当前环境记录

本次终端环境下：

1. `cmd /c mvn -v`：不可用；  
2. `wsl mvn -v`：`E_ACCESSDENIED`；  
3. 因此 Sonar 扫描未在当前终端直接执行。  

结论：请在 IDE Maven 或 CI Runner 中执行第 2 章命令并回填结果。

---

（文件结束）
