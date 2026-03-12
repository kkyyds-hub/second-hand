# 管理端真实后端联调说明

## 启动方式

### 1) 纯前端 Mock 模式

```bash
npm run dev:mock
```

说明：
- 使用本地 mock 数据；
- 适合后端还没启动时继续做页面开发；
- 商品审核、用户管理、纠纷列表都可在前端本地跑流程。

### 2) 真实后端联调模式

```bash
npm run dev:real
```

说明：
- `VITE_USE_MOCK=false`
- 前端通过 Vite 代理把 `/api/*` 转发到 `http://localhost:8080`
- 你的 Spring Boot 后端默认端口也是 `8080`

---

## 当前已对齐的真实接口

### 登录
- `POST /admin/employee/login`

请求体：

```json
{
  "loginId": "手机号或邮箱",
  "password": "密码"
}
```

---

### 首页总览
- `GET /admin/dashboard/overview`

---

### 用户管理
- `GET /admin/user`
- `POST /admin/user`
- `PUT /admin/user/{userId}/ban?reason=...`
- `PUT /admin/user/{userId}/unban`
- `GET /admin/user/export`

---

### 商品审核
- `GET /admin/products/pending-approval`
- `PUT /admin/products/{productId}/approve`
- `PUT /admin/products/{productId}/reject`

说明：
- 驳回接口需要请求体：

```json
{
  "reason": "驳回原因"
}
```

- 前端页面已补驳回原因弹窗，可直接联调。

---

### 纠纷与违规
- `GET /admin/audit/overview`
- `PUT /admin/after-sales/{afterSaleId}/arbitrate`
- `PUT /admin/products/reports/{ticketNo}/resolve`

说明：
- 当前真实后端已提供总览查询；
- 页面列表/筛选/详情可直接联调；
- 页面已补真实处理弹窗：
  - 交易纠纷：支持售后 / 驳回售后
  - 违规举报：举报不成立 / 强制下架商品
- 风控线索当前仍以“查询联调”为主，未接统一写入动作。

---

## 当前真实联调时的已知限制

### 1. 商品审核列表字段不完全齐
后端 `ProductDTO` 当前稳定返回：
- `productId`
- `productName`
- `category`
- `status`
- `submitTime`

前端已做兼容：
- 卖家名缺失时显示 `--`
- 价格缺失时显示 `--`
- 风险等级按类目做前端兜底展示

### 2. 举报工单依赖 ticketNo
前端当前用总览返回的 `id` 作为举报处理时的 `ticketNo`。

如果后端某条举报工单没有生成 `ticketNo`，而是退回了类似 `RPT-数字ID` 的兜底值，
则该条工单的“立即处理”接口可能无法命中真实工单编号。

---

## 推荐验证顺序

1. 先起后端（确认是 `localhost:8080`）
2. 前端运行：

```bash
npm run dev:real
```

3. 验证顺序：
   1. 登录
   2. 首页总览
   3. 用户列表 / 筛选 / 封禁 / 解封
   4. 商品审核列表 / 通过 / 驳回
   5. 纠纷与违规页列表 / 筛选 / 详情

---

## 如果联调失败优先排查

1. 后端是否运行在 `http://localhost:8080`
2. 登录是否返回 `token`
3. 请求头是否带了 `token`
4. 后端统一返回是否仍是：

```json
{
  "code": 1,
  "msg": "success",
  "data": {}
}
```

5. 商品审核列表是否返回 `PageResult`
