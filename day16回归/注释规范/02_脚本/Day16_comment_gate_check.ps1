Param(
    [string]$Root = "."
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Set-Location $Root
$regressionDirs = Get-ChildItem -Directory | Where-Object { $_.Name -like "day*" } | ForEach-Object { $_.FullName }
$reportDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$javaFiles = rg --files demo-service demo-common demo-pojo -g "*.java"
$metrics = @()
$missingP0 = @()

foreach ($f in $javaFiles) {
    $lines = Get-Content -Encoding UTF8 $f
    $nonEmpty = ($lines | Where-Object { $_.Trim().Length -gt 0 }).Count
    $commentCount = ($lines | Where-Object { $_ -match "^\s*(//|/\*|\*|\*/)" }).Count
    $density = if ($nonEmpty -eq 0) { 0 } else { [math]::Round(($commentCount * 100.0 / $nonEmpty), 2) }
    $metrics += [pscustomobject]@{
        File     = $f
        NonEmpty = $nonEmpty
        Comment  = $commentCount
        Density  = $density
    }

    if ($f -match "demo-service/src/main/java/.*/controller/" -or
        $f -match "demo-service/src/main/java/.*/service/" -or
        $f -match "demo-service/src/main/java/.*/service/serviceimpl/") {
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            if ($line -match "^\s*public\s+" -and $line -match "\(" -and $line -notmatch "\b(class|interface|enum|record)\b") {
                $k = $i - 1
                while ($k -ge 0 -and ($lines[$k].Trim().Length -eq 0 -or $lines[$k].Trim().StartsWith("@"))) { $k-- }
                $hasJavaDoc = $false
                if ($k -ge 0 -and $lines[$k].Trim().StartsWith("*/")) {
                    $j = $k
                    while ($j -ge 0) {
                        if ($lines[$j].Trim().StartsWith("/**")) { $hasJavaDoc = $true; break }
                        if ($lines[$j].Trim().StartsWith("/*") -and -not $lines[$j].Trim().StartsWith("/**")) { break }
                        $j--
                    }
                }
                elseif ($k -ge 0 -and $lines[$k].Trim().StartsWith("/**")) {
                    $hasJavaDoc = $true
                }
                if (-not $hasJavaDoc) {
                    $missingP0 += [pscustomobject]@{
                        File      = $f
                        Line      = ($i + 1)
                        Signature = $line.Trim()
                    }
                }
            }
        }
    }
}

$zeroComment = @($metrics | Where-Object { $_.Comment -eq 0 })
$lowDensity = @($metrics | Where-Object { $_.Density -lt 10 })

$pattern = "用户ID|用户Id|用户id|商品ID|商品Id|商品id|订单ID|订单Id|订单id|地址ID|地址Id|地址id|收货地址ID|创建Time|更新Time|分页查詢|分页查找"
$scanPaths = @("demo-service", "demo-common", "demo-pojo") + $regressionDirs
$termHitsRaw = & rg -n -S --glob "!*Day16_Comment_Standard_Plan_v1.0.md" --glob "!*Day16_term_drift_hits.txt" --glob "!*Day16_comment_gate_check.ps1" $pattern @scanPaths
$garbledHitsRaw = & rg -n -S --glob "!*Day16_garbled_hits.txt" --glob "!*Day16_comment_gate_check.ps1" "�|锟斤拷|Ã|Â" @scanPaths

$termHits = @(); if ($termHitsRaw) { $termHits = @($termHitsRaw) }
$garbledHits = @(); if ($garbledHitsRaw) { $garbledHits = @($garbledHitsRaw) }

$metrics | Sort-Object Density | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $reportDir "Day16_comment_metrics.csv")
$missingP0 | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $reportDir "Day16_p0_missing_javadoc.csv")
$zeroComment | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $reportDir "Day16_zero_comment.csv")
$lowDensity | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $reportDir "Day16_low_density.csv")

if ($termHits.Count -gt 0) { $termHits | Set-Content -Encoding UTF8 (Join-Path $reportDir "Day16_term_drift_hits.txt") } else { "" | Set-Content -Encoding UTF8 (Join-Path $reportDir "Day16_term_drift_hits.txt") }
if ($garbledHits.Count -gt 0) { $garbledHits | Set-Content -Encoding UTF8 (Join-Path $reportDir "Day16_garbled_hits.txt") } else { "" | Set-Content -Encoding UTF8 (Join-Path $reportDir "Day16_garbled_hits.txt") }

$summary = [pscustomobject]@{
    JAVA_TOTAL                 = $metrics.Count
    ZERO_COMMENT              = $zeroComment.Count
    LOW_DENSITY_LT10          = $lowDensity.Count
    P0_PUBLIC_MISSING_JAVADOC = $missingP0.Count
    TERM_DRIFT_HITS           = $termHits.Count
    GARBLED_HITS              = $garbledHits.Count
}

$summary | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $reportDir "Day16_gate_summary.json")
$summary | Format-List
