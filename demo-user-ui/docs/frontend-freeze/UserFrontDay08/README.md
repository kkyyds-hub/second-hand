# UserFrontDay08 文档总览

- 日期：`2026-03-18`
- 状态：`计划建档完成（待 UserFrontDay01 收口后接棒）`
- 主题：`共享交互、错误治理、系统通知与 API 模块整治`
- 当日目标：为系统通知、未读数、跨域 loading / empty / error / submit 规则，以及用户端 API 模块消费边界建立统一的共享治理入口。

---

## 1. 当天一句话结论

`UserFrontDay08` 已完成计划建档，但尚未进入代码实现或联调执行；它的作用是把“共享交互、错误治理、系统通知与 API 模块整治”从前序 Days 中独立出来，形成可持续回填的唯一入口。

---

## 2. 为什么 UserFrontDay08 接在 Day07 之后

1. 当 Day02~Day07 的业务域逐步明确后，用户端需要一个独立治理日来统一交互规则、错误处理与跨域 API 模块边界。
2. 后端已存在 `MessageController` 的未读数 / 系统通知入口，而前端尚无通知中心、统一 badge 与错误治理基线。
3. 若不先建立 Day08 入口，系统通知、全局提示、空态 / 错误态和 API 模块整治会散落到各业务线程里。

---

## 3. 做什么 / 不做什么 / 退出标准

| 分类 | 内容 |
|---|---|
| UserFrontDay08 要做什么 | 冻结系统通知 / 未读数、共享 loading / empty / error / submit 规则、跨域 API 模块命名与页面消费边界，以及 Day09 之前必须统一的共享治理口径。 |
| UserFrontDay08 不做什么 | 不重开 Day02~Day07 的业务语义；不把系统通知写成完整消息中心已完成；不把 Day08 文档当成 Day09 运行通过的替代品。 |
| UserFrontDay08 退出标准 | 系统通知、共享交互、API 模块整治三组子流都已有明确 owner、规则口径、回填入口和代表性验证清单。 |
| UserFrontDay08 输出物 | `README`、`01_冻结文档`、`02_接口对齐`、`03_API模块`、`04_联调准备与验收`、`05_进度回填` 六个入口统一到“共享交互、错误治理、系统通知与 API 模块整治”主题。 |

---

## 4. 推荐阅读顺序

1. `01_冻结文档/UserFrontDay08_Scope_Freeze_v1.0.md`
2. `02_接口对齐/UserFrontDay08_Interface_Alignment_v1.0.md`
3. `03_API模块/UserFrontDay08_API_Module_Plan_v1.0.md`
4. `04_联调准备与验收/UserFrontDay08_Joint_Debug_Ready_v1.0.md`
5. `05_进度回填/UserFrontDay08_Progress_Backfill_v1.0.md`
6. `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`

---

## 5. 覆盖业务域

| 业务域 | 当前前端基线 | 本日承担 |
|---|---|---|
| 系统通知与未读数 | 当前无通知列表页、详情页、未读 badge 与已读策略。 | 承接系统通知列表 / 详情 / 全部已读 / 未读数的前端入口。 |
| 共享交互与错误治理 | 当前缺少跨页面统一的 loading / empty / error / submit 规则。 | 承接代表性列表页、表单页、提交态与错误反馈的统一治理口径。 |
| 共享 API 模块整治 | 当前只有 `auth.ts`、`seller.ts` 两个最小模块，其余业务域尚未形成统一命名和适配边界。 | 承接 Day02~Day07 新模块的命名、消费边界和错误映射规则。 |

---

## 6. 与当前执行日衔接

- 当前执行日仍是 `UserFrontDay01`，Day08 当前只是“已建档、待接棒”。
- 若 Day08 启动时需要回引 Day06 的订单会话边界，应注明“订单会话归 Day06、系统通知归 Day08”。
- 若共享错误治理需要修改 Day01 的 request/auth 基线，应先回填 Day01，再同步修正 Day08 文档。
