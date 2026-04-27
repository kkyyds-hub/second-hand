# UserFrontDay02 范围冻结（Scope Freeze）

- 日期：`2026-04-22`
- 文档版本：`v1.3`
- 当前状态：`已完成并回填（2026-04-22 final acceptance docs-only adjudication 完成）`
- 当日主题：`账户中心补强与地址管理`

---

## 1. Day02 最终冻结范围

1. 账户资料补强：昵称 / 简介编辑、头像上传配置获取、头像上传、资料写回与 session/profile 同步；
2. 账号安全与绑定：修改密码（current-password 路径）、手机号绑定/解绑、邮箱绑定/解绑；
3. 收货地址管理：地址列表只读起步、地址新增 create-only、地址编辑 edit-only、默认地址切换 set-default、地址删除 delete-only；
4. 沿用基线：继续复用 Day01 已冻结的 `request.ts`、`router/index.ts`、`AccountCenterPage.vue` 账户域壳，不重写 Day01 已完成结论；
5. 非 Day02 范围：市场浏览、收藏、用户商品、订单、钱包、积分、信用、通知与跨域治理，仍归 Day03+。

---

## 2. 最终裁定结果（docs-only）

- 裁定结论：`UserFrontDay02` 可升级为 `已完成并回填`。
- 裁定依据：
  1. Day02 owned scope 内未发现新的关键子流证据缺口；
  2. 十个关键子流均已有运行态或 joint-debug 级运行证据并完成回填；
  3. 历史头像上传 `backend / contract-gap` 已在 `UserFrontDay02_Progress_Backfill_v2.3.md` 与 `UserFrontDay02_Joint_Debug_Ready_v2.3.md` 中关闭；
  4. 本轮 docs-only 终裁未发现新的 code / controller / runtime 真值冲突，因此无需继续维持 `待最终裁定`。

---

## 3. Day02 关键子流闭环范围

1. 资料编辑 focused regression：`PATCH /user/me/profile` + session 回写；
2. 地址只读起步：`/account/addresses` + `GET /user/addresses` + loading/empty/error/retry；
3. 地址新增 create-only：`POST /user/addresses`；
4. 地址编辑 edit-only：详情回填 + `PUT /user/addresses/{id}`；
5. 地址默认切换 set-default：`PUT /user/addresses/{id}/default`；
6. 地址删除 delete-only：`DELETE /user/addresses/{id}`；
7. 修改密码 current-password：`POST /api/user/me/password`，改密后改回闭环；
8. 手机绑定/解绑：`POST/DELETE /api/user/me/bindings/phone` + `localStorage.user_profile.mobile` 写回确认；
9. 邮箱绑定/解绑：`POST/DELETE /api/user/me/bindings/email` + `localStorage.user_profile.email` 写回确认；
10. 头像上传：`POST /api/user/me/upload-config` -> `PUT /user/me/avatar/upload` -> `PATCH /api/user/me/profile` + session/avatar 写回确认。

---

## 4. 必须保留的边界

1. Day02 `已完成并回填` 只覆盖 Day02 owned scope，不等于 Day03+、整站联调或整站冻结完成；
2. 地址 `edit-only / set-default / delete-only` 的运行证据仍属于浏览器可控 mock 运行态，已被显式纳入 Day02 终裁边界，但不能外推成整站真实联调通过；
3. 手机绑定/解绑的运行证据主要落在 `v2.1` 联调/回填文档中，属于允许的 joint-debug 级 running evidence；
4. 用户端工作继续只回填到 `demo-user-ui/docs/frontend-freeze/`，不写回 `demo-admin-ui/docs/frontend-freeze/`。

---

## 5. 退出与顺延规则

1. `UserFrontDay02` 于 `2026-04-22` 完成 final acceptance docs-only adjudication 后退出当前执行日；
2. root 当前执行日应顺延至当前最早仍未收口的 `UserFrontDay04`；
3. 不直接跳转到 Day06+，因为 Day04 仍处于 `已具备收口材料，待最终裁定` 状态。
