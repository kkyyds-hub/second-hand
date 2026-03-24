# UserFrontDay02 API 模块规划

- 日期：`2026-03-23`
- 文档版本：`v1.1`
- 当前状态：`已正式接棒（文档接棒完成，待进入实现 / 联调）`

---

## 1. 模块目标

把 Day02 涉及的账户资料、安全中心、地址管理落到明确的 API 文件、页面消费边界、session 回写规则与后续回填入口；当前只完成规划，不创建任何前端代码文件。

---

## 2. 首个最小切片的 API 规划

| 模块 / 文件 | 当前基础 | Day02 规划 | 当前优先级 |
|---|---|---|---|
| `demo-user-ui/src/api/profile.ts` | 待创建 | 作为 Day02 第一批新增 API 模块，先封装 `PATCH /user/me/profile`，后续再扩展头像上传与安全中心。 | 第一刀 |
| `demo-user-ui/src/pages/AccountCenterPage.vue` | Day01 已存在 | 作为首个消费者页面，先承接昵称 / 简介编辑与提交结果展示。 | 第一刀 |
| `demo-user-ui/src/utils/request.ts` | Day01 已存在 | 继续提供 `authentication` 请求头、401 清理与统一请求基础。 | 沿用基线 |
| `demo-user-ui/src/utils/request.ts` 中的 `saveCurrentUser` | Day01 已存在 | 作为资料编辑成功后的本地 session 回写入口，避免页面直接操作 localStorage。 | 第一刀 |
| `demo-user-ui/src/api/address.ts` | 待创建 | 统一管理地址域，不塞进 `seller.ts` 或 `auth.ts`。 | 后续子流 |
| `demo-user-ui/src/router/index.ts` | Day01 已存在 | 后续若新增账户设置 / 地址管理路由，仍需复用 Day01 守卫。 | 后续子流 |

---

## 3. Day02 API 规则

1. `auth.ts` 只保留登录 / 注册 / 激活职责；资料编辑和账号安全不回流到 `auth.ts`；
2. 资料变更成功后，必须通过统一 helper 回写本地 session，不能在多个页面各自维护 localStorage；
3. Day02 第一刀先打通 `profile.ts + AccountCenterPage.vue + saveCurrentUser` 这条最小闭环，再扩到头像上传 / 安全绑定 / 地址模块；
4. 地址列表默认按分页接口消费，新增 / 编辑共用表单时也必须保留提交路径区分；
5. API 模块命名、返回值适配和错误提示都必须为后续回填留出证据路径。

---

## 4. 当前字段与适配口径

| 模块 | 当前口径 | Day02 处理方式 |
|---|---|---|
| 账户资料展示 | 依赖 `readCurrentUser()` 的本地 session 数据 | 首切片执行时补“patch 成功后如何用 `saveCurrentUser()` 刷新展示”的单一方案。 |
| 资料编辑请求 | `UpdateProfileRequest` 当前包含 `nickname / avatar / bio` | 第一刀先覆盖 `nickname / bio`，`avatar` 随头像上传子流再进入。 |
| 头像上传 | 当前仅确认后端有 `upload-config` 与 `avatar/upload` 两步接口 | 执行时补字段级适配，当前只冻结双阶段流程。 |
| 安全中心 | 当前没有前端页面与类型定义 | 执行时在 `profile.ts` 或独立安全模块中统一定义请求参数和返回适配。 |
| 地址分页 | 控制器默认 `page=1`、`pageSize=20` | 待进入地址子流时统一封装，不在页面里手工拼接。 |