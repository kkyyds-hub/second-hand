# Day9 回归测试（isSeller 卖家校验 + Seller Summary）

## 目录结构（建议放到项目根目录）
```
day9回归/
  Day9_Regression.postman_collection.json
  Day9_Local.postman_environment.json
```

## Newman 运行
```powershell
newman run ".\day9回归\Day9_Regression.postman_collection.json" `
  -e ".\day9回归\Day9_Local.postman_environment.json" `
  --reporters cli
```

## 覆盖点
- ✅ 卖家统计：GET /user/seller/summary（卖家成功、买家失败）
- ✅ 卖家列表：GET /user/products、GET /user/orders/sell（卖家成功、买家失败）
- ✅ 卖家商品 CRUD：创建/编辑/下架/删除（卖家成功）
- ✅ 买家越权：买家调用卖家接口统一返回 msg=“仅卖家可执行该操作”


## v2 patch
- Fix auth header injection (avoid skipping when placeholder exists)
- Fix Seller Summary fields assertion keys
- Print Seller Summary response body via console.log for faster debugging
