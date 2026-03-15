# FrontDay05 联调准备与验收

- 日期：`2026-03-14`
- 文档版本：`v1.1`
- 当前状态：`已完成并回填`

---

## 1. 环境前置条件

1. 后端服务可访问，默认以 `http://localhost:8080` 为准。
2. 前端以 `demo-admin-ui` 项目运行，常用命令为 `npm run dev:real`。
3. 管理端有效登录态可用，允许首页直接发起真实接口请求。

---

## 2. 本轮保留的验收证据

1. `npm.cmd run build` 通过。
2. Dashboard 页面可正常打开，不白屏、不报路由错误。
3. `GET /admin/dashboard/overview`、`GET /admin/statistics/dau`、`GET /admin/statistics/order-gmv`、`GET /admin/statistics/product-publish` 在真实模式下可访问。
4. 当真实返回为 `0` 时，页面按真实 `0` 展示，不再强造 demo 数字。
5. 趋势图仍保留本地占位这一事实被明确记录，而不是被误写成“已联调通过”。

---

## 3. 已确认结果

1. `2026-03-14` 已完成首页核心真实接口核验。
2. `2026-03-15` 在当前管理员重新登录后的真实页面走查中，前端实际触发了：
   - `/api/admin/dashboard/overview?date=2026-03-15`
   - `/api/admin/statistics/dau?date=2026-03-15`
   - `/api/admin/statistics/order-gmv?date=2026-03-15`
   - `/api/admin/statistics/product-publish?date=2026-03-15`
   - `/api/admin/audit/overview?riskLevel=HIGH`
3. 同轮页面实测中，Dashboard 展示 `GMV=¥0 / 订单数=0 / 发布量=0` 的真实结果，并保留高优处理队列的只读兜底。

---

## 4. 不阻塞 Day05 的后续项

1. 趋势图真实数据替换。
2. 更大范围视觉回归与窄屏验证。
3. 更细粒度扩展统计能力补强。

这些项仍可继续推进，但不再作为 Day05 的未完成理由。

---

## 5. 验收结论

Day05 的验收目标已经达成：首页真实接口边界和文档主入口都已冻结清楚，因此 Day05 可以确定为 `已完成并回填`。
