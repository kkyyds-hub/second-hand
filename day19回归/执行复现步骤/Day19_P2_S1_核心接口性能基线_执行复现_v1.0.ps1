param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Rounds = 3,
    [int]$WarmupRequests = 60,
    [int]$MeasuredRequests = 300,
    [int]$Concurrency = 12,
    [int]$TimeoutSec = 15,
    [string]$OutputDir = "day19回归/执行记录",
    [string]$OrderNo = "2026030410531818448",
    [decimal]$OrderAmount = 99
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percent
    )
    if ($null -eq $Values -or $Values.Count -eq 0) {
        return $null
    }
    $sorted = $Values | Sort-Object
    $index = [int][Math]::Ceiling($Percent * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [Math]::Round([double]$sorted[$index], 2)
}

function Summarize-Run {
    param(
        [object[]]$Rows,
        [datetime]$StartedAt,
        [datetime]$EndedAt
    )

    if ($null -eq $Rows) { $Rows = @() }
    $total = $Rows.Count
    $okRows = @($Rows | Where-Object { $_.success -eq $true })
    $failRows = @($Rows | Where-Object { $_.success -ne $true })
    $latencies = @($Rows | ForEach-Object { [double]$_.latencyMs })

    $durationSec = [Math]::Max(0.001, ($EndedAt - $StartedAt).TotalSeconds)
    $throughput = [Math]::Round(($total / $durationSec), 2)
    $errorRate = if ($total -eq 0) { 1.0 } else { [Math]::Round(($failRows.Count * 100.0 / $total), 4) }

    $errorGroups = @($failRows |
        Group-Object -Property errorType, httpStatus, businessCode, errorMessage |
        Sort-Object Count -Descending |
        Select-Object -First 10 |
        ForEach-Object {
            [pscustomobject]@{
                key = $_.Name
                count = $_.Count
            }
        })

    return [pscustomobject]@{
        totalRequests = $total
        successRequests = $okRows.Count
        failedRequests = $failRows.Count
        errorRatePct = $errorRate
        throughputRps = $throughput
        p50Ms = Get-Percentile -Values $latencies -Percent 0.50
        p95Ms = Get-Percentile -Values $latencies -Percent 0.95
        minMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Minimum).Minimum), 2) } else { $null }
        maxMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Maximum).Maximum), 2) } else { $null }
        durationSec = [Math]::Round($durationSec, 3)
        errorGroupsTop10 = $errorGroups
    }
}

function Invoke-ScenarioRun {
    param(
        [string]$ScenarioName,
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [string]$ContentType,
        [string]$BodyTemplate,
        [string]$BodyMode,
        [int]$TotalRequests,
        [int]$Concurrency,
        [int]$TimeoutSec,
        [int]$RoundNo,
        [string]$Phase
    )

    $perWorker = [int][Math]::Floor($TotalRequests / $Concurrency)
    $remainder = $TotalRequests % $Concurrency

    $jobs = @()
    $startedAt = Get-Date

    for ($w = 0; $w -lt $Concurrency; $w++) {
        $workCount = $perWorker + $(if ($w -lt $remainder) { 1 } else { 0 })
        if ($workCount -le 0) {
            continue
        }

        $jobs += Start-Job -ScriptBlock {
            param(
                [string]$ScenarioName,
                [string]$Method,
                [string]$Uri,
                [hashtable]$Headers,
                [string]$ContentType,
                [string]$BodyTemplate,
                [string]$BodyMode,
                [int]$WorkCount,
                [int]$TimeoutSec,
                [int]$RoundNo,
                [string]$Phase,
                [int]$WorkerId
            )

            $rows = New-Object System.Collections.Generic.List[object]

            for ($i = 0; $i -lt $WorkCount; $i++) {
                $reqStart = Get-Date
                $httpStatus = $null
                $businessCode = $null
                $success = $false
                $errorType = $null
                $errorMessage = $null

                try {
                    $body = $null
                    if ($Method -ne "GET") {
                        if ($BodyMode -eq "callback") {
                            $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
                            $tradeNo = "P2S1-$WorkerId-$RoundNo-$i-" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                            $body = $BodyTemplate.Replace("__TIMESTAMP__", [string]$ts).Replace("__TRADE_NO__", $tradeNo)
                        } else {
                            $body = $BodyTemplate
                        }
                    }

                    if ($Method -eq "GET") {
                        $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec $TimeoutSec
                    } else {
                        $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -ContentType $ContentType -Body $body -TimeoutSec $TimeoutSec
                    }

                    $httpStatus = [int]$resp.StatusCode
                    $parsed = $null
                    try {
                        $parsed = $resp.Content | ConvertFrom-Json
                    } catch {
                        $parsed = $null
                    }

                    if ($null -ne $parsed -and $null -ne $parsed.code) {
                        $businessCode = [int]$parsed.code
                    }

                    if ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $businessCode -eq 1) {
                        $success = $true
                    } else {
                        if ($httpStatus -lt 200 -or $httpStatus -ge 300) {
                            $errorType = "HTTP"
                            $errorMessage = "HTTP_STATUS_$httpStatus"
                        } elseif ($null -eq $businessCode) {
                            $errorType = "PARSE"
                            $errorMessage = "MISSING_BUSINESS_CODE"
                        } else {
                            $errorType = "BUSINESS"
                            $errorMessage = "BUSINESS_CODE_$businessCode"
                        }
                    }
                } catch {
                    $ex = $_.Exception
                    $errorType = "EXCEPTION"
                    $errorMessage = $ex.Message
                    $respObj = $null
                    if ($null -ne $ex -and $null -ne $ex.Response) {
                        $respObj = $ex.Response
                    }
                    if ($null -ne $respObj) {
                        try {
                            $httpStatus = [int]$respObj.StatusCode.value__
                        } catch {
                            $httpStatus = $null
                        }
                    }
                }

                $latencyMs = [Math]::Round(((Get-Date) - $reqStart).TotalMilliseconds, 2)
                $rows.Add([pscustomobject]@{
                    scenario = $ScenarioName
                    phase = $Phase
                    round = $RoundNo
                    workerId = $WorkerId
                    index = $i
                    requestAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
                    method = $Method
                    uri = $Uri
                    latencyMs = $latencyMs
                    httpStatus = $httpStatus
                    businessCode = $businessCode
                    success = $success
                    errorType = $errorType
                    errorMessage = $errorMessage
                })
            }

            return $rows
        } -ArgumentList $ScenarioName, $Method, $Uri, $Headers, $ContentType, $BodyTemplate, $BodyMode, $workCount, $TimeoutSec, $RoundNo, $Phase, $w
    }

    $rows = @()
    if ($jobs.Count -gt 0) {
        $rows = Receive-Job -Job $jobs -Wait
        Remove-Job -Job $jobs -Force -ErrorAction SilentlyContinue
    }

    $endedAt = Get-Date
    $summary = Summarize-Run -Rows $rows -StartedAt $startedAt -EndedAt $endedAt

    return [pscustomobject]@{
        startedAt = $startedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
        endedAt = $endedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
        phase = $Phase
        round = $RoundNo
        summary = $summary
        rows = $rows
    }
}

function Merge-RoundSummaries {
    param(
        [object[]]$RunResults
    )

    $allRows = @()
    $totalDuration = 0.0
    foreach ($r in $RunResults) {
        $allRows += @($r.rows)
        $totalDuration += [double]$r.summary.durationSec
    }

    if ($totalDuration -lt 0.001) { $totalDuration = 0.001 }

    $latencies = @($allRows | ForEach-Object { [double]$_.latencyMs })
    $total = $allRows.Count
    $successCount = @($allRows | Where-Object { $_.success -eq $true }).Count
    $failedCount = $total - $successCount
    $errorRate = if ($total -eq 0) { 1.0 } else { [Math]::Round(($failedCount * 100.0 / $total), 4) }
    $throughput = [Math]::Round(($total / $totalDuration), 2)

    $errorGroups = @($allRows |
        Where-Object { $_.success -ne $true } |
        Group-Object -Property errorType, httpStatus, businessCode, errorMessage |
        Sort-Object Count -Descending |
        Select-Object -First 10 |
        ForEach-Object {
            [pscustomobject]@{
                key = $_.Name
                count = $_.Count
            }
        })

    return [pscustomobject]@{
        totalRequests = $total
        successRequests = $successCount
        failedRequests = $failedCount
        errorRatePct = $errorRate
        throughputRps = $throughput
        p50Ms = Get-Percentile -Values $latencies -Percent 0.50
        p95Ms = Get-Percentile -Values $latencies -Percent 0.95
        minMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Minimum).Minimum), 2) } else { $null }
        maxMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Maximum).Maximum), 2) } else { $null }
        durationSec = [Math]::Round($totalDuration, 3)
        rounds = $RunResults.Count
        errorGroupsTop10 = $errorGroups
    }
}

$now = Get-Date
$tsFile = $now.ToString("yyyy-MM-dd_HH-mm-ss")
$outputPath = Join-Path $OutputDir ("Day19_P2_S1_动态结果_" + $tsFile + ".json")

if (-not (Test-Path $OutputDir)) {
    New-Item -Path $OutputDir -ItemType Directory -Force | Out-Null
}

# preflight: login and token
$loginBody = '{"loginId":"13800000001","password":"123456"}'
$loginResp = Invoke-RestMethod -Uri ($BaseUrl + "/user/auth/login/password") -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec $TimeoutSec
if ($loginResp.code -ne 1 -or -not $loginResp.data.token) {
    throw "Preflight failed: user login failed, cannot get token."
}
$userToken = [string]$loginResp.data.token

$preflight = [ordered]@{
    userLoginCode = [int]$loginResp.code
    userId = [int]$loginResp.data.user.id
    userMobileMasked = "138****0001"
    tokenAcquired = $true
    orderNo = $OrderNo
    orderAmount = [string]$OrderAmount
}

$scenarios = @(
    [pscustomobject]@{
        name = "login_password"
        chain = "登录"
        method = "POST"
        uri = $BaseUrl + "/user/auth/login/password"
        headers = @{}
        contentType = "application/json"
        bodyTemplate = '{"loginId":"13800000001","password":"123456"}'
        bodyMode = "static"
        warmupRequests = $WarmupRequests
        measuredRequests = $MeasuredRequests
        concurrency = $Concurrency
    },
    [pscustomobject]@{
        name = "market_product_list"
        chain = "商品列表"
        method = "GET"
        uri = $BaseUrl + "/user/market/products?page=1&pageSize=20"
        headers = @{ authentication = $userToken }
        contentType = ""
        bodyTemplate = ""
        bodyMode = "none"
        warmupRequests = $WarmupRequests
        measuredRequests = $MeasuredRequests
        concurrency = $Concurrency
    },
    [pscustomobject]@{
        name = "payment_callback"
        chain = "支付回调"
        method = "POST"
        uri = $BaseUrl + "/payment/callback"
        headers = @{}
        contentType = "application/json"
        bodyTemplate = ('{"channel":"mock","orderNo":"' + $OrderNo + '","tradeNo":"__TRADE_NO__","amount":' + $OrderAmount + ',"status":"SUCCESS","timestamp":__TIMESTAMP__,"sign":"day19-perf-sign"}')
        bodyMode = "callback"
        warmupRequests = $WarmupRequests
        measuredRequests = $MeasuredRequests
        concurrency = $Concurrency
    }
)

$scenarioOutputs = @()

foreach ($sc in $scenarios) {
    # warmup
    $null = Invoke-ScenarioRun -ScenarioName $sc.name -Method $sc.method -Uri $sc.uri -Headers $sc.headers -ContentType $sc.contentType -BodyTemplate $sc.bodyTemplate -BodyMode $sc.bodyMode -TotalRequests $sc.warmupRequests -Concurrency $sc.concurrency -TimeoutSec $TimeoutSec -RoundNo 0 -Phase "warmup"

    $roundResults = @()
    for ($r = 1; $r -le $Rounds; $r++) {
        $run = Invoke-ScenarioRun -ScenarioName $sc.name -Method $sc.method -Uri $sc.uri -Headers $sc.headers -ContentType $sc.contentType -BodyTemplate $sc.bodyTemplate -BodyMode $sc.bodyMode -TotalRequests $sc.measuredRequests -Concurrency $sc.concurrency -TimeoutSec $TimeoutSec -RoundNo $r -Phase "measure"
        $roundResults += $run
    }

    $baseline = Merge-RoundSummaries -RunResults $roundResults

    $scenarioOutputs += [pscustomobject]@{
        name = $sc.name
        chain = $sc.chain
        endpoint = ($sc.method + " " + ($sc.uri.Replace($BaseUrl, "")))
        method = $sc.method
        uri = $sc.uri
        params = [pscustomobject]@{
            rounds = $Rounds
            warmupRequests = $sc.warmupRequests
            measuredRequestsPerRound = $sc.measuredRequests
            totalMeasuredRequests = $sc.measuredRequests * $Rounds
            concurrency = $sc.concurrency
            timeoutSec = $TimeoutSec
        }
        rounds = @($roundResults | ForEach-Object {
            [pscustomobject]@{
                round = $_.round
                startedAt = $_.startedAt
                endedAt = $_.endedAt
                totalRequests = $_.summary.totalRequests
                successRequests = $_.summary.successRequests
                failedRequests = $_.summary.failedRequests
                errorRatePct = $_.summary.errorRatePct
                throughputRps = $_.summary.throughputRps
                p50Ms = $_.summary.p50Ms
                p95Ms = $_.summary.p95Ms
                minMs = $_.summary.minMs
                maxMs = $_.summary.maxMs
                durationSec = $_.summary.durationSec
                errorGroupsTop10 = $_.summary.errorGroupsTop10
            }
        })
        baseline = $baseline
    }
}

$summary = [ordered]@{}
foreach ($s in $scenarioOutputs) {
    $summary[$s.name] = [ordered]@{
        chain = $s.chain
        endpoint = $s.endpoint
        p50Ms = $s.baseline.p50Ms
        p95Ms = $s.baseline.p95Ms
        errorRatePct = $s.baseline.errorRatePct
        throughputRps = $s.baseline.throughputRps
        totalMeasuredRequests = $s.baseline.totalRequests
    }
}

$result = [ordered]@{
    meta = [ordered]@{
        runAt = $now.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        script = "day19回归/执行复现步骤/Day19_P2_S1_核心接口性能基线_执行复现_v1.0.ps1"
        note = "业务成功口径: HTTP 2xx 且 Result.code=1"
    }
    config = [ordered]@{
        rounds = $Rounds
        warmupRequests = $WarmupRequests
        measuredRequests = $MeasuredRequests
        concurrency = $Concurrency
        timeoutSec = $TimeoutSec
    }
    preflight = $preflight
    scenarios = $scenarioOutputs
    summary = $summary
}

$result | ConvertTo-Json -Depth 20 | Set-Content -Path $outputPath -Encoding UTF8
Write-Output $outputPath
