# Day17 Step P5-S3：质量门禁执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：复现 Day17 的代码质量门禁与 SQL 质量门禁结果。

---

## 1. 前置条件

1. 项目路径：`c:\Users\kk\Desktop\demo`。  
2. 数据库可访问（用于 EXPLAIN 复核）。  
3. 若执行 Sonar：本机可用 Maven + Sonar 服务可访问。

---

## 2. 功能基线回归（G-1）

执行：

```bash
cmd /c newman run "day17回归\Day17_Regression.postman_collection.json" -e "day17回归\Day17_Local_Regression.postman_environment.json" --reporters cli,json,junit --reporter-json-export "day17回归\执行记录\day17_newman_result.json" --reporter-junit-export "day17回归\执行记录\day17_newman_result.xml"
```

验收：
1. `Failed requests = 0`  
2. `Failed assertions = 0`

---

## 3. 代码质量等价门禁（G-2/G-3）

### 3.1 扫描命令

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'

# 技术债标记
rg -n "TODO|FIXME|HACK" demo-service/src/main/java demo-common/src/main/java demo-pojo/src/main/java

# 标准输出/堆栈打印
rg -n "printStackTrace\(|System\.out\.print|System\.err\.print" demo-service/src/main/java demo-common/src/main/java demo-pojo/src/main/java

# 通用异常捕获
rg -n "catch \(Exception" demo-service/src/main/java demo-common/src/main/java demo-pojo/src/main/java
```

### 3.2 判定口径

1. Day17 核心链路不得新增阻断级模式（例如无日志吞异常、无保护空指针）。  
2. 历史问题可登记为技术债，但必须写入处置清单。  
3. 结果回填：`day17回归/质量门禁/Day17_P5_S3_问题处置清单_v1.0.md`。

---

## 4. SQL 质量门禁（G-4）

### 4.1 复核 EXPLAIN 文档

1. `day17回归/慢SQL与索引治理/Day17_P3_S3_EXPLAIN结论_v1.0.md`  
2. `day17回归/复杂SQL治理/Day17_P2_S3_EXPLAIN结论_v1.0.md`  
3. `day17回归/慢SQL与索引治理/Day17_P3_S4_Outbox_filesort收口_v1.0.md`

### 4.2 抽样 EXPLAIN 复跑命令（示例）

```sql
EXPLAIN SELECT id,user_id,is_default,updated_at
FROM addresses
WHERE user_id=1
ORDER BY is_default DESC, updated_at DESC
LIMIT 0,20;

EXPLAIN SELECT id,product_id,status
FROM product_violations
WHERE product_id=920005 AND status='active'
ORDER BY id DESC
LIMIT 0,20;

EXPLAIN SELECT id,status,next_retry_time
FROM message_outbox
WHERE status IN ('NEW','FAIL')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY id ASC
LIMIT 200;
```

---

## 5. Sonar 复现（可选但推荐）

执行说明文件：
- `day17回归/质量门禁/Day17_P5_S3_Sonar_接入说明_v1.0.md`

验收：
1. `Blocker = 0`  
2. 关键告警已在处置清单闭环

---

## 6. DoD 验收勾选

- [ ] 功能回归通过（0 失败断言）  
- [ ] Day17 范围无阻断级质量问题遗留  
- [ ] 关键 SQL 有 EXPLAIN 与索引证据  
- [ ] 问题处置清单完整、可审计

---

（文件结束）
