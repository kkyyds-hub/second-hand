# 测试MySQL数据库连接
$hostname = "localhost"
$port = 3306
$database = "secondhand2"
$username = "root"
$password = "1234"

Write-Host "正在测试MySQL数据库连接..." -ForegroundColor Yellow
Write-Host "主机: $hostname" -ForegroundColor Cyan
Write-Host "端口: $port" -ForegroundColor Cyan
Write-Host "数据库: $database" -ForegroundColor Cyan
Write-Host ""

# 测试端口是否开放
try {
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    $connection = $tcpClient.BeginConnect($hostname, $port, $null, $null)
    $wait = $connection.AsyncWaitHandle.WaitOne(3000, $false)
    
    if ($wait) {
        $tcpClient.EndConnect($connection)
        Write-Host "✓ MySQL端口 $port 连接成功！" -ForegroundColor Green
        $tcpClient.Close()
    } else {
        Write-Host "✗ MySQL端口 $port 连接超时" -ForegroundColor Red
        Write-Host "  请检查MySQL服务是否已启动" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ 无法连接到MySQL服务器: $_" -ForegroundColor Red
    Write-Host "  请检查MySQL服务是否已启动" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "配置文件位置:" -ForegroundColor Yellow
Write-Host "  - .vscode/settings.json" -ForegroundColor Cyan
Write-Host "  - .sqltools/connections.json" -ForegroundColor Cyan
Write-Host ""
Write-Host "如果端口测试通过，请在Cursor中:" -ForegroundColor Yellow
Write-Host "  1. 按 Ctrl+Shift+P" -ForegroundColor White
Write-Host "  2. 输入 'SQLTools: Refresh Connections'" -ForegroundColor White
Write-Host "  3. 在SQLTools面板中点击连接" -ForegroundColor White

