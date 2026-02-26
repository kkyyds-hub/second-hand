# Day18 P7-S1 功能回归执行记录 v1.0

- 日期：2026-02-26
- 对应步骤：`Step P7-S1：功能回归脚本化`
- 关联复现文档：`day18回归/执行复现步骤/Day18_P7_S1_功能回归脚本化_执行复现_v1.0.md`

---

## 1. 回归范围

1. 复跑 Day17 全量回归分组（01~05）。
2. 增补 Day18 分组（06）并新增以下断言：
   - Outbox 缺失事件触发语义稳定（非异常、`updatedRows=0`）；
   - 订单异常打标重复请求幂等；
   - 用户封禁/解封重复请求幂等分流。

---

## 2. 执行信息

1. 集合：`day18回归/Day18_Regression_v2.postman_collection.json`
2. 环境：`day18回归/Day18_Local_Regression_v2.postman_environment.json`
3. 命令：

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
cmd /c npx --yes newman run "day18回归/Day18_Regression_v2.postman_collection.json" -e "day18回归/Day18_Local_Regression_v2.postman_environment.json" --reporters cli,json,junit --reporter-json-export "day18回归/执行记录/Day18_P7_S1_newman_result.json" --reporter-junit-export "day18回归/执行记录/Day18_P7_S1_newman_result.xml"
```

4. 执行时间：2026-02-26

---

## 3. 结果摘要

| 指标 | 结果 |
|---|---:|
| Iterations | 1 |
| Requests | 45 |
| Test Scripts | 45 |
| Assertions | 106 |
| Failed Requests | 0 |
| Failed Assertions | 0 |
| 平均响应时间 | 40ms |

结论：回归通过。

---

## 4. 关键分组结果

1. `01-Auth`：通过。
2. `02-Day17-Setup`：通过。
3. `03-Day17-MP-CRUD`：通过。
4. `04-Day17-Pagination-And-Mixed`：通过。
5. `05-Day17-Idempotency-And-Async`：通过。
6. `06-Day18-Consistency-And-Safety`：通过。

---

## 5. Day18 新增断言结果

1. Outbox 缺失事件触发：通过（`updatedRows == 0`，`success == false`）。
2. 订单打标重复提交：通过（第二次返回“订单已存在该类型标记”）。
3. 用户封禁重复提交：通过（第二次返回“用户已处于封禁状态”）。
4. 用户解封重复提交：通过（第二次返回“用户已处于正常状态”）。

---

## 6. 产物归档

1. `day18回归/执行记录/Day18_P7_S1_newman_result.json`
2. `day18回归/执行记录/Day18_P7_S1_newman_result.xml`

---

## 7. DoD 回填

| DoD 项 | 结果 | 说明 |
|---|---|---|
| 核心链路回归通过 | `[x]` | 45 请求、106 断言全部通过 |
| 结果可由他人复现 | `[x]` | 已固定命令、环境文件与报告输出路径 |

---

（文件结束）
