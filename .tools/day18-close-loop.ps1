# 兼容入口：转发到中文脚本（Day18_闭环回归验证.ps1）
$target = Join-Path $PSScriptRoot 'Day18_闭环回归验证.ps1'
if (-not (Test-Path $target)) {
    throw "中文脚本不存在: $target"
}
& $target @args
exit $LASTEXITCODE
