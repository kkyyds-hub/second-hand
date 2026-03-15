# FrontDay03 API 模块计划

- 日期：`2026-03-12`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填`

---

## 1. 模块目标

把当天相关的页面能力落到明确的 API 文件、字段映射、错误处理和 mock/real 策略上。

---

## 2. 重点文件

| 文件 | 角色 | 当日要求 |
|---|---|---|
| `demo-admin-ui/src/pages/products/ProductReview.vue` | 商品审核页面与弹窗交互 | 作为 Day03 的核心页面。 |
| `demo-admin-ui/src/api/product.ts` | 商品审核 API 与字段兜底 | 包含状态映射、价格格式化、字段兼容逻辑。 |
| `demo-admin-ui/src/pages/settings/SystemSettings.vue` | 系统设置占位页 | 当前只承接菜单可达与结构占位。 |
| `demo-admin-ui/docs/backend-real-linkup.md` | 商品审核真实接口说明 | 作为接口对齐补充文档。 |

---

## 3. 当日 API 规则

1. 商品审核页字段兼容逻辑保留在 `src/api/product.ts`，不要散落到模板层。
2. 缺失卖家名、价格、风险等级时沿用现有兜底显示。
3. 系统设置页如仍无真实接口，必须保持“建设中/占位”语义一致。

---

## 4. 预期输出

1. 接口口径与页面行为对应关系明确。
2. 字段映射、兜底、错误处理位置清晰。
3. 后续若改代码，可直接定位到当天关联文件。
