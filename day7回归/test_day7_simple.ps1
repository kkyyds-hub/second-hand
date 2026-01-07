# Day 7 接口简单测试脚本

$baseUrl = "http://localhost:8080"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Day 7 接口测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 测试1: 检查服务健康
Write-Host "[测试1] 检查服务状态..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/doc.html" -Method Get -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
    Write-Host "✓ 服务运行正常 (HTTP $($response.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "✗ 服务未启动: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 测试2: 待审列表接口（会返回401，但可以验证接口存在）
Write-Host "[测试2] GET /admin/products/pending-approval (需要管理员Token)" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/products/pending-approval?page=1&pageSize=10" -Method Get -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
    Write-Host "✓ 接口可访问 (HTTP $($response.StatusCode))" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ 接口存在，返回401（需要认证）- 符合预期" -ForegroundColor Green
    } else {
        Write-Host "✗ 接口访问失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# 测试3: 测试 status=全部 参数
Write-Host "[测试3] GET /admin/products/pending-approval?status=全部" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/products/pending-approval?page=1&pageSize=10&status=全部" -Method Get -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
    Write-Host "✓ 接口可访问 (HTTP $($response.StatusCode))" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ 接口存在，返回401（需要认证）- 符合预期" -ForegroundColor Green
    } else {
        Write-Host "✗ 接口访问失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# 测试4: 测试具体状态参数
Write-Host "[测试4] GET /admin/products/pending-approval?status=on_sale" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/products/pending-approval?page=1&pageSize=10&status=on_sale" -Method Get -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
    Write-Host "✓ 接口可访问 (HTTP $($response.StatusCode))" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✓ 接口存在，返回401（需要认证）- 符合预期" -ForegroundColor Green
    } else {
        Write-Host "✗ 接口访问失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# 数据库验证
Write-Host "[数据库验证] 检查测试数据..." -ForegroundColor Yellow
$mysqlPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$result = echo "SELECT COUNT(*) as under_review_count FROM products WHERE is_deleted = 0 AND status = 'under_review';" | & "$mysqlPath\mysql.exe" -u root -p1234 secondhand2 --skip-column-names 2>&1 | Select-String -Pattern "^\d+"
if ($result) {
    Write-Host "✓ 数据库中有 $result 条 under_review 状态的商品" -ForegroundColor Green
} else {
    Write-Host "⚠ 未找到 under_review 状态的商品" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "基础测试完成！" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步操作：" -ForegroundColor Yellow
Write-Host "1. 打开浏览器访问: http://localhost:8080/doc.html" -ForegroundColor White
Write-Host "2. 登录管理员账号获取Token" -ForegroundColor White
Write-Host "3. 在Knife4j中测试以下接口：" -ForegroundColor White
Write-Host "   - GET /admin/products/pending-approval" -ForegroundColor Gray
Write-Host "   - PUT /admin/products/{id}/approve" -ForegroundColor Gray
Write-Host "   - PUT /admin/products/{id}/reject" -ForegroundColor Gray
Write-Host ""
Write-Host "详细测试步骤请参考: Day7接口测试指南.md" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
