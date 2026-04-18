# UserFrontDay04 文档总览

- 日期：`2026-04-18`
- 状态：`已具备收口材料，待最终裁定（基于包1+包2既有证据）`
- 主题：`用户商品管理与卖家工作台扩展`
- 当日目标：为后续执行线程提供可直接开工的 Day04 输入包（仅文档，不含实现与联调结论）。

---

## 1. 当天一句话结论

`UserFrontDay04` 已具备“包1+包2”收口材料并完成本轮 docs-only 裁定回填；当前口径为“已具备收口材料，待最终裁定”，不写成“已完成并回填”。

---

## 2. 为什么 Day04 接在 Day03 之后

1. Day01 已有 `HomePage.vue` + `seller.ts` 的卖家摘要基线，但那只是首页摘要，不等于独立卖家工作台。
2. 后端已存在 `SellerController` 与 `UserProductController`，而用户端前端还没有对应的工作台页面、商品列表页和商品表单。
3. 若不先建立 Day04 独立输入包，卖家工作台和用户商品管理容易被误写进 Day01/Day05，导致回填和验收口径冲突。

---

## 3. Day04 owned scope / 非范围

| 分类 | 内容 |
|---|---|
| Day04 owned scope | 卖家工作台扩展（基于 `GET /user/seller/summary`）、卖家“我的商品”列表/详情/发布/编辑/删除、商品状态流转（`off-shelf`/`resubmit`/`on-shelf` 兼容语义/`withdraw`）。 |
| Day04 非范围 | 购物车与下单、买家订单、卖家订单履约与物流、买家/卖家售后、钱包、积分、信用、系统通知、共享治理日工作。 |
| 边界规则 | 不把 Day01 首页摘要写成“卖家工作台已完成”；不把 Day04 写成“已开工完成/已联调通过”。 |

---

## 4. Day04 执行输入包与当前回填

### 4.1 最小读取

1. `demo-user-ui/docs/frontend-freeze/README.md`（确认 root active day 仍是 Day02）
2. `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`（确认 Day04 归属行）
3. `demo-user-ui/docs/frontend-freeze/UserFrontDay04/01_冻结文档/UserFrontDay04_Scope_Freeze_v1.0.md`
4. `demo-user-ui/docs/frontend-freeze/UserFrontDay04/02_接口对齐/UserFrontDay04_Interface_Alignment_v1.0.md`
5. `demo-user-ui/docs/frontend-freeze/UserFrontDay04/03_API模块/UserFrontDay04_API_Module_Plan_v1.0.md`
6. `demo-user-ui/docs/frontend-freeze/UserFrontDay04/04_联调准备与验收/UserFrontDay04_Joint_Debug_Ready_v1.1.md`
7. `demo-user-ui/docs/frontend-freeze/UserFrontDay04/05_进度回填/UserFrontDay04_Progress_Backfill_v1.5.md`

### 4.2 推荐执行顺序（后续执行线程）

1. 先做 `seller.ts` 消费边界与卖家工作台路由入口（不改业务结论文案）。
2. 再做 `userProducts.ts` 与列表/详情只读链路。
3. 再做发布/编辑/删除与状态流转按钮（含失败态）。
4. 当前第二包已补齐 create 驱动最小真实闭环证据；后续仅需在最终 acceptance 线程做裁定回填。

### 4.3 升级条件（升级到 `$drive-demo-user-ui-delivery`）

出现以下任一情况即升级：

- `on-shelf` 与 `resubmit` 语义冲突，需要跨前端 API 层 + 后端控制器统一；
- 401 / token / session 行为导致卖家域页面无法稳定复现；
- 运行态结论需要同轮修复代码、验证、回填联动闭环。

若仅做 docs-only 规划与归属修订，不触发升级。

---

## 5. 推荐阅读顺序

1. `01_冻结文档/UserFrontDay04_Scope_Freeze_v1.0.md`
2. `02_接口对齐/UserFrontDay04_Interface_Alignment_v1.0.md`
3. `03_API模块/UserFrontDay04_API_Module_Plan_v1.0.md`
4. `04_联调准备与验收/UserFrontDay04_Joint_Debug_Ready_v1.1.md`
5. `05_进度回填/UserFrontDay04_Progress_Backfill_v1.5.md`
6. `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`

---

## 6. 当天模块清单

| 模块 | 作用 | 当前状态 |
|---|---|---|
| 冻结文档 | 定义 Day04 范围、非目标、退出口径 | `v1.0 已创建（计划建档）` |
| 接口对齐 | 冻结 `SellerController`、`UserProductController` 的入口口径 | `v1.0 已创建（计划建档）` |
| API 模块 | 规划 `seller / userProducts` 模块与页面消费边界 | `v1.0 已创建（计划建档）` |
| 联调准备与验收 | 固定卖家工作台与商品管理的验证路径并记录当前 runtime 结论 | `v1.1 已更新（第二包 create 驱动最小闭环 pass）` |
| 进度回填 | 记录 Day04 第一包/第二包 runtime verify 与 acceptance 裁定回填 | `v1.5 已更新（已具备收口材料，待最终裁定）` |

---

## 7. 与当前执行日衔接

- root 当前执行日仍是 `UserFrontDay02`（待最终裁定），本次不切换到 Day04。
- Day04 当前已具备第一包/第二包 runtime 证据，但仍不代表“Day04 已完成”或“最终 acceptance 已通过”。
- 若 Day04 启动时需要引用账户资料中的卖家身份字段，应引用 Day02 既有结论，不回写到 `demo-admin-ui`。

## 8. Day04 第一包/第二包 runtime verify（含第二包重跑回填）

- 回填日期：`2026-04-18`

### 8.1 第一包（只读链路）

- 回填来源：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package1-runtime-verify/`
- 本包结论：`pass（只读链路）`
- 已回填子流：
  1. 未登录访问 `/seller` 鉴权守卫（重定向到 `/login?redirect=/seller`）
  2. 卖家工作台只读链路（`GET /api/user/seller/summary`）
  3. 商品列表只读链路（`GET /api/user/products`，含筛选请求观测）
  4. 商品详情只读链路（`GET /api/user/products/{productId}`）

### 8.2 第二包（写链路补充验证）

- 回填来源：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/`
- 主链结论：`pass`（`create/edit/withdraw/resubmit/off-shelf/on-shelf/delete`）
- create 关键结论：`POST /api/user/products` 返回 `code=1`，成功产出 `createdProductId=920128`，后续主链全部跑通。
- 历史 blocker 处理：create 前置条件（信用等级）已在环境层完成修复并留证（`precondition-fix-evidence.md`）。

### 8.3 状态与升级判断

- Day04 口径更新为：`已具备收口材料，待最终裁定`（不写成“Day04 已完成并回填”）。
- 升级判断：`needDriveDelivery=false`，且本轮可收回到非升级态（问题已证实为环境前置条件并已消除）。
