# 兼容入口：转发到中文脚本（Day19_P1_S1_观测入口与口径校验.ps1）
$target = Join-Path $PSScriptRoot 'Day19_P1_S1_观测入口与口径校验.ps1'
if (-not (Test-Path $target)) {
    throw "中文脚本不存在: $target"
}
& $target @args
exit $LASTEXITCODE
