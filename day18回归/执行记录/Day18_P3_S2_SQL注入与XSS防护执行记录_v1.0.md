# Day18 P3-S2 SQL 注入与 XSS 防护执行记录 v1.0

- 日期：2026-02-25
- 关联复现文档：`day18回归/执行复现步骤/Day18_P3_S2_SQL注入与XSS防护_执行复现_v1.0.md`
- 当前状态：已执行并完成回填。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. 执行人：`Codex`
3. 执行时间：`2026-02-25 14:05:00 ~ 14:34:02`
4. 原始证据：`day18回归/执行记录/Day18_P3_S2_动态验证结果_2026-02-25_14-34-02.json`

---

## 2. 场景执行结果

| 场景 | 操作 | 预期 | 实际结果 | 是否通过 |
|---|---|---|---|---|
| A SQL 静态扫描 | 扫描 `${}` / 排序白名单守卫 | 无动态 SQL 拼接入口 | `mapper` 未命中 `${}`；排序入口已接入 `normalizeSortField/normalizeSortOrder` | `[x]` |
| B 排序注入动态验证 | 恶意 `sortField` 请求 | 被白名单拒绝 | 返回 `code=0`，提示 `sortField 仅支持 createTime/payTime` | `[x]` |
| C XSS 输入动态验证 | profile/report 输入脚本片段 | 被输入守卫拒绝 | `PATCH /user/me/profile`、`POST /user/market/products/{id}/report` 均返回 `code=0`，提示包含非法脚本或 HTML 片段 | `[x]` |

---

## 3. DoD 勾选（回填区）

- [x] 不存在动态 SQL 拼接注入入口。  
- [x] 关键富文本/字符串入口具备 XSS 处理规则。  
- [x] 已形成执行证据（扫描命令 + 动态返回 + 原始结果文件）。  

---

（文件结束）
