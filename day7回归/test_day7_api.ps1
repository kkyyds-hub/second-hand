# Day 7 接口测试脚本

$baseUrl = "http://localhost:8080"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Day 7 接口测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 等待服务启动
Write-Host "[1] 检查服务是否启动..." -ForegroundColor Yellow
$maxRetries = 30
$retryCount = 0
$isRunning = $false

while ($retryCount -lt $maxRetries) {
    try {
        $response = Invoke-WebRequest -Uri "$baseUrl/doc.html" -Method Get -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            $isRunning = $true
            Write-Host "✓ 服务已启动！" -ForegroundColor Green
            break
        }
    } catch {
        $retryCount++
        Write-Host "等待服务启动... ($retryCount/$maxRetries)" -ForegroundColor Gray
        Start-Sleep -Seconds 2
    }
}

if (-not $isRunning) {
    Write-Host "✗ 服务未启动，请先启动项目！" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 测试1: 待审列表（默认查询 under_review）
Write-Host "[测试1] GET /admin/products/pending-approval (默认查询)" -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/products/pending-approval?page=1&pageSize=10" -Method Get -Headers @{"authentication"="test"} -ErrorAction Stop
    Write-Host "✓ 请求成功" -ForegroundColor Green
    Write-Host "  返回数据: code=$($response.code)" -ForegroundColor Gray
    if ($response.data) {
        Write-Host "  总记录数: $($response.data.total)" -ForegroundColor Gray
        Write-Host "  当前页: $($response.data.page)" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ 请求失败: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试2: 待审列表（查询全部）
Write-Host "[测试2] GET /admin/products/pending-approval?status=全部" -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/products/pending-approval?page=1&pageSize=10&status=全部" -Method Get -Headers @{"authentication"="test"} -ErrorAction Stop
    Write-Host "✓ 请求成功" -ForegroundColor Green
    Write-Host "  返回数据: code=$($response.code)" -ForegroundColor Gray
    if ($response.data) {
        Write-Host "  总记录数: $($response.data.total)" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ 请求失败: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试3: 待审列表（查询具体状态）
Write-Host "[测试3] GET /admin/products/pending-approval?status=on_sale" -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/products/pending-approval?page=1&pageSize=10&status=on_sale" -Method Get -Headers @{"authentication"="test"} -ErrorAction Stop
    Write-Host "✓ 请求成功" -ForegroundColor Green
    Write-Host "  返回数据: code=$($response.code)" -ForegroundColor Gray
    if ($response.data) {
        Write-Host "  总记录数: $($response.data.total)" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ 请求失败: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "测试完成！" -ForegroundColor Cyan
Write-Host "注意: 审核通过/驳回接口需要有效的管理员Token" -ForegroundColor Yellow
Write-Host "     请使用 Knife4j (http://localhost:8080/doc.html) 进行完整测试" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
