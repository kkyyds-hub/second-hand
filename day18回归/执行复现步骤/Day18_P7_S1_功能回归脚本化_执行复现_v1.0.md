# Day18 P7-S1 功能回归脚本化 执行复现 v1.0

- 日期：2026-02-26
- 目标：复跑 Day17 回归集合并补 Day18 一致性/安全断言，形成可复现命令与固定产物路径。

---

## 1. 前置条件

1. 服务已启动并可访问：`http://localhost:8080`。
2. 测试数据满足本地回归账号：买家/卖家/管理员账号可登录。
3. Node.js 与 `npx` 可用。
4. 由于 PowerShell 执行策略可能拦截 `newman.ps1`，统一使用 `cmd /c npx --yes newman` 执行。

---

## 2. 回归集合与环境

1. 集合（v2）：`day18回归/Day18_Regression_v2.postman_collection.json`
2. 环境（v2）：`day18回归/Day18_Local_Regression_v2.postman_environment.json`

说明：
1. `v2` 在 Day17 全量用例基础上追加 `06-Day18-Consistency-And-Safety` 分组。
2. 新增断言覆盖：
   - Outbox 缺失事件触发重试的稳定语义（`updatedRows=0`）；
   - 订单打标重复提交幂等；
   - 用户封禁/解封重复操作幂等分流语义。

---

## 3. 执行命令（固定）

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
cmd /c npx --yes newman run "day18回归/Day18_Regression_v2.postman_collection.json" ^
  -e "day18回归/Day18_Local_Regression_v2.postman_environment.json" ^
  --reporters cli,json,junit ^
  --reporter-json-export "day18回归/执行记录/Day18_P7_S1_newman_result.json" ^
  --reporter-junit-export "day18回归/执行记录/Day18_P7_S1_newman_result.xml"
```

---

## 4. 产物路径（固定）

1. `day18回归/执行记录/Day18_P7_S1_newman_result.json`
2. `day18回归/执行记录/Day18_P7_S1_newman_result.xml`
3. `day18回归/执行记录/Day18_P7_S1_功能回归执行记录_v1.0.md`

---

## 5. DoD 勾选

- [ ] 核心链路回归通过。  
- [ ] 结果可由他人按固定命令与路径复现。  

---

（文件结束）
