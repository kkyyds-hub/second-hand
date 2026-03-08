# Day17 Newman 执行记录 v1.0

- 日期：2026-02-24
- 集合：`day17回归/Day17_Regression.postman_collection.json`
- 环境：`day17回归/Day17_Local_Regression.postman_environment.json`

---

## 1. 执行命令

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
npx --yes newman run "day17回归/Day17_Regression.postman_collection.json" `
  -e "day17回归/Day17_Local_Regression.postman_environment.json" `
  --reporters cli,json,junit `
  --reporter-json-export "day17回归/执行记录/day17_newman_result.json" `
  --reporter-junit-export "day17回归/执行记录/day17_newman_result.xml"
```

---

## 2. 执行结果（复跑通过）

- Started at：2026-02-24（本地）
- Ended at：2026-02-24（本地）
- Total requests：37
- Total assertions：81
- Failed requests：0
- Failed assertions：0
- 结论：回归通过。

---

## 3. 分组结果（复跑通过）

1. `01-Auth`：通过。  
2. `02-Day17-Setup`：通过。  
3. `03-Day17-MP-CRUD`：通过。  
4. `04-Day17-Pagination-And-Mixed`：通过。  
5. `05-Day17-Idempotency-And-Async`：通过。  

---

## 4. 失败明细（回填）

1. Request：
   - Error：
   - Root cause：
   - Retry result：

---

## 5. 结论

- 是否达到冻结标准（是/否）：是  
- 未通过项与后续动作：无（若后续新增用例，按同命令增量回归）。  

---

（文件结束）
