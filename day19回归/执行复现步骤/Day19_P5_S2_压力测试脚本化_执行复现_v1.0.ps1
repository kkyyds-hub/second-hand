[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [ValidateSet("smoke", "standard", "stress-lite")]
    [string]$Profile = "standard",
    [int]$Rounds = 0,
    [int]$WarmupRequests = 0,
    [int]$MeasuredRequests = 0,
    [int]$Concurrency = 0,
    [int]$TimeoutSec = 0,
    [string]$OutputDir = "",
    [string]$LoginId = "13800000001",
    [string]$Password = "123456",
    [string]$OrderNo = "",
    [decimal]$OrderAmount = 0,
    [string]$MySqlHost = "localhost",
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = "secondhand2",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "1234",
    [long]$SellerUserId = 2
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::UTF8
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

function ConvertFrom-CodePoints {
    param(
        [int[]]$CodePoints
    )

    return (-join ($CodePoints | ForEach-Object { [char]$_ }))
}

$executionRecordFolderName = ConvertFrom-CodePoints -CodePoints @(0x6267, 0x884C, 0x8BB0, 0x5F55)
$resultFileLabel = ConvertFrom-CodePoints -CodePoints @(0x538B, 0x529B, 0x6D4B, 0x8BD5, 0x7ED3, 0x679C)

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path (Split-Path -Parent $PSScriptRoot) $executionRecordFolderName
}

$profileMap = @{
    "smoke" = [ordered]@{
        rounds = 2
        warmupRequests = 20
        measuredRequests = 80
        concurrency = 6
        timeoutSec = 10
    }
    "standard" = [ordered]@{
        rounds = 3
        warmupRequests = 60
        measuredRequests = 300
        concurrency = 12
        timeoutSec = 15
    }
    "stress-lite" = [ordered]@{
        rounds = 3
        warmupRequests = 80
        measuredRequests = 500
        concurrency = 24
        timeoutSec = 20
    }
}

$selectedProfile = $profileMap[$Profile]
$effectiveRounds = if ($Rounds -gt 0) { $Rounds } else { [int]$selectedProfile.rounds }
$effectiveWarmupRequests = if ($WarmupRequests -gt 0) { $WarmupRequests } else { [int]$selectedProfile.warmupRequests }
$effectiveMeasuredRequests = if ($MeasuredRequests -gt 0) { $MeasuredRequests } else { [int]$selectedProfile.measuredRequests }
$effectiveConcurrency = if ($Concurrency -gt 0) { $Concurrency } else { [int]$selectedProfile.concurrency }
$effectiveTimeoutSec = if ($TimeoutSec -gt 0) { $TimeoutSec } else { [int]$selectedProfile.timeoutSec }
$script:MySqlCli = $null
$script:RunPrefix = "DAY19-P5-S2-$([DateTime]::Now.ToString('yyyyMMddHHmmss'))"

function Ensure-MySqlCli {
    if ($script:MySqlCli) {
        return
    }

    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -eq $mysqlCommand) {
        throw "mysql CLI not found."
    }

    $script:MySqlCli = $mysqlCommand.Source
}

function Invoke-DbText {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql
    )

    Ensure-MySqlCli
    $env:MYSQL_PWD = $MySqlPassword
    try {
        $output = & $script:MySqlCli `
            -h $MySqlHost `
            -P $MySqlPort `
            -u $MySqlUser `
            -D $MySqlDatabase `
            --default-character-set=utf8mb4 `
            --batch `
            --raw `
            -N `
            -e $Sql
        return ($output -join "`n").Trim()
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Invoke-DbScalar {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql
    )

    $text = Invoke-DbText -Sql $Sql
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    $firstLine = ($text -split "`r?`n")[0].Trim()
    if ($firstLine -eq "NULL") {
        return $null
    }

    return $firstLine
}

function Convert-ToSqlLiteral {
    param([AllowNull()][object]$Value)

    if ($null -eq $Value) {
        return "NULL"
    }

    if ($Value -is [bool]) {
        return $(if ($Value) { "1" } else { "0" })
    }

    if ($Value -is [int] -or $Value -is [long] -or $Value -is [decimal] -or $Value -is [double]) {
        return [string]$Value
    }

    return "'" + ([string]$Value).Replace("'", "''") + "'"
}

function Invoke-ApiJsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [hashtable]$Headers,
        [AllowNull()][object]$Body,
        [int]$TimeoutSec = 20
    )

    try {
        if ($Method -eq "GET") {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec $TimeoutSec
        } elseif ($null -eq $Body) {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -TimeoutSec $TimeoutSec
        } else {
            $jsonBody = $Body | ConvertTo-Json -Compress -Depth 10
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec $TimeoutSec
        }
    } catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
            try {
                $content = $reader.ReadToEnd()
            } finally {
                $reader.Close()
            }
            throw "API request failed: $content"
        }

        throw
    }

    $parsed = $null
    try {
        $parsed = $resp.Content | ConvertFrom-Json
    } catch {
        $parsed = $null
    }

    return [pscustomobject]@{
        statusCode = [int]$resp.StatusCode
        body = $parsed
        rawBody = $resp.Content
    }
}

function New-TestProduct {
    param(
        [Parameter(Mandatory = $true)]
        [decimal]$Price
    )

    $title = "$($script:RunPrefix)-CALLBACK"
    $sql = @(
        "INSERT INTO products (",
        "    owner_id, title, description, price, images, category, status, view_count, reason, is_deleted, create_time, update_time",
        ") VALUES (",
        "    $SellerUserId,",
        "    $(Convert-ToSqlLiteral $title),",
        "    $(Convert-ToSqlLiteral 'Day19 P5-S2 pressure callback sample'),",
        "    $Price,",
        "    $(Convert-ToSqlLiteral 'https://example.com/day19-p5-s2.png'),",
        "    $(Convert-ToSqlLiteral 'day19'),",
        "    'on_sale',",
        "    0,",
        "    NULL,",
        "    0,",
        "    NOW(),",
        "    NOW()",
        ");",
        "SELECT LAST_INSERT_ID();"
    ) -join "`n"

    $productId = [long](Invoke-DbScalar -Sql $sql)
    return [pscustomobject]@{
        productId = $productId
        title = $title
        ownerId = $SellerUserId
        price = $Price
    }
}

function New-PendingOrderSample {
    param(
        [Parameter(Mandatory = $true)]
        [long]$ProductId,
        [Parameter(Mandatory = $true)]
        [hashtable]$UserHeaders
    )

    $request = @{
        productId = $ProductId
        shippingAddress = "Day19 P5-S2 sample address"
        quantity = 1
    }

    $response = Invoke-ApiJsonRequest -Method "POST" -Uri ($BaseUrl + "/user/orders") -Headers $UserHeaders -Body $request -TimeoutSec $effectiveTimeoutSec
    if ($response.statusCode -lt 200 -or $response.statusCode -ge 300 -or $null -eq $response.body -or $response.body.code -ne 1 -or $null -eq $response.body.data) {
        throw "Create pending order failed: $($response.rawBody)"
    }

    return [pscustomobject]@{
        orderId = [long]$response.body.data.orderId
        orderNo = [string]$response.body.data.orderNo
        status = [string]$response.body.data.status
        totalAmount = [decimal]$response.body.data.totalAmount
    }
}

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

function Test-ServiceAlive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Reason,
        [int]$ProbeTimeoutSec = 5
    )

    $probeUri = $BaseUrl + "/user/market/products?page=1&pageSize=1"

    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $probeUri -Method Get -TimeoutSec $ProbeTimeoutSec
        return [pscustomobject]@{
            alive = $true
            reason = $Reason
            statusCode = [int]$resp.StatusCode
            detail = "HTTP_$([int]$resp.StatusCode)"
        }
    } catch {
        $response = $null
        try {
            $response = $_.Exception.Response
        } catch {
            $response = $null
        }

        if ($null -ne $response) {
            $statusCode = $null
            try {
                $statusCode = [int]$response.StatusCode.value__
            } catch {
                $statusCode = $null
            }

            return [pscustomobject]@{
                alive = $true
                reason = $Reason
                statusCode = $statusCode
                detail = if ($null -ne $statusCode) { "HTTP_$statusCode" } else { "HTTP_ERROR_WITH_RESPONSE" }
            }
        }

        return [pscustomobject]@{
            alive = $false
            reason = $Reason
            statusCode = $null
            detail = $_.Exception.Message
        }
    }
}

function Assert-ServiceAlive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Reason
    )

    $probe = Test-ServiceAlive -Reason $Reason -ProbeTimeoutSec ([Math]::Max(3, [Math]::Min($effectiveTimeoutSec, 8)))
    if (-not $probe.alive) {
        throw "Service unavailable during [$Reason]. Detail: $($probe.detail)"
    }

    return $probe
}

function Get-InterviewVerdict {
    param(
        [double]$ErrorRatePct,
        [int]$ConnectivityFailureCount
    )

    if ($ConnectivityFailureCount -gt 0) {
        return "FAIL"
    }
    if ($ErrorRatePct -le 0) {
        return "PASS"
    }
    if ($ErrorRatePct -le 1) {
        return "MINOR"
    }
    return "FAIL"
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
    $connectivityFailures = @($Rows | Where-Object { $_.connectivityFailure -eq $true })

    $durationSec = [Math]::Max(0.001, ($EndedAt - $StartedAt).TotalSeconds)
    $throughput = [Math]::Round(($total / $durationSec), 2)
    $errorRate = if ($total -eq 0) { 100.0 } else { [Math]::Round(($failRows.Count * 100.0 / $total), 4) }

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
        connectivityFailureCount = $connectivityFailures.Count
        errorRatePct = $errorRate
        throughputRps = $throughput
        p50Ms = Get-Percentile -Values $latencies -Percent 0.50
        p95Ms = Get-Percentile -Values $latencies -Percent 0.95
        minMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Minimum).Minimum), 2) } else { $null }
        maxMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Maximum).Maximum), 2) } else { $null }
        durationSec = [Math]::Round($durationSec, 3)
        verdict = Get-InterviewVerdict -ErrorRatePct $errorRate -ConnectivityFailureCount $connectivityFailures.Count
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
                $connectivityFailure = $false

                try {
                    $body = $null
                    if ($Method -ne "GET") {
                        if ($BodyMode -eq "callback") {
                            $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
                            $tradeNo = "P5S2-$WorkerId-$RoundNo-$i-" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
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
                    } else {
                        $connectivityFailure = $true
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
                    connectivityFailure = $connectivityFailure
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
    $connectivityFailures = @($allRows | Where-Object { $_.connectivityFailure -eq $true }).Count
    $errorRate = if ($total -eq 0) { 100.0 } else { [Math]::Round(($failedCount * 100.0 / $total), 4) }
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
        connectivityFailureCount = $connectivityFailures
        errorRatePct = $errorRate
        throughputRps = $throughput
        p50Ms = Get-Percentile -Values $latencies -Percent 0.50
        p95Ms = Get-Percentile -Values $latencies -Percent 0.95
        minMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Minimum).Minimum), 2) } else { $null }
        maxMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Maximum).Maximum), 2) } else { $null }
        durationSec = [Math]::Round($totalDuration, 3)
        rounds = $RunResults.Count
        verdict = Get-InterviewVerdict -ErrorRatePct $errorRate -ConnectivityFailureCount $connectivityFailures
        errorGroupsTop10 = $errorGroups
    }
}

if (-not (Test-Path $OutputDir)) {
    New-Item -Path $OutputDir -ItemType Directory -Force | Out-Null
}

$now = Get-Date
$tsFile = $now.ToString("yyyy-MM-dd_HH-mm-ss")
$outputPath = Join-Path $OutputDir ("Day19_P5_S2_" + $resultFileLabel + "_" + $tsFile + ".json")
$scriptPathForJson = [System.IO.Path]::GetFullPath($MyInvocation.MyCommand.Path)

$preflightProbe = Assert-ServiceAlive -Reason "preflight probe"

$loginBody = ('{"loginId":"' + $LoginId + '","password":"' + $Password + '"}')
$loginResp = Invoke-RestMethod -Uri ($BaseUrl + "/user/auth/login/password") -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec $effectiveTimeoutSec
if ($loginResp.code -ne 1 -or -not $loginResp.data.token) {
    throw "Preflight failed: user login failed, cannot get token."
}

$userToken = [string]$loginResp.data.token
$callbackSampleSource = "provided"
$callbackProduct = $null
$callbackOrder = $null
$effectiveOrderNo = $OrderNo
$effectiveOrderAmount = $OrderAmount

if ([string]::IsNullOrWhiteSpace($effectiveOrderNo) -or $effectiveOrderAmount -le 0) {
    $callbackSampleSource = "auto-generated"
    $callbackProduct = New-TestProduct -Price 109.00
    $callbackOrder = New-PendingOrderSample -ProductId $callbackProduct.productId -UserHeaders @{ authentication = $userToken }
    $effectiveOrderNo = $callbackOrder.orderNo
    $effectiveOrderAmount = [decimal]$callbackOrder.totalAmount
}

$preflight = [ordered]@{
    serviceAlive = $true
    probeStatus = $preflightProbe.detail
    userLoginCode = [int]$loginResp.code
    userId = [int]$loginResp.data.user.id
    userMobileMasked = if ($LoginId.Length -ge 7) { $LoginId.Substring(0, 3) + "****" + $LoginId.Substring($LoginId.Length - 4) } else { $LoginId }
    tokenAcquired = $true
    callbackSampleSource = $callbackSampleSource
    orderNo = $effectiveOrderNo
    orderAmount = [string]$effectiveOrderAmount
    callbackProductId = if ($null -ne $callbackProduct) { [long]$callbackProduct.productId } else { $null }
    callbackOrderId = if ($null -ne $callbackOrder) { [long]$callbackOrder.orderId } else { $null }
}

$scenarios = @(
    [pscustomobject]@{
        name = "login_password"
        chain = "Login"
        method = "POST"
        uri = $BaseUrl + "/user/auth/login/password"
        headers = @{}
        contentType = "application/json"
        bodyTemplate = ('{"loginId":"' + $LoginId + '","password":"' + $Password + '"}')
        bodyMode = "static"
        warmupRequests = $effectiveWarmupRequests
        measuredRequests = $effectiveMeasuredRequests
        concurrency = $effectiveConcurrency
    },
    [pscustomobject]@{
        name = "market_product_list"
        chain = "MarketProductList"
        method = "GET"
        uri = $BaseUrl + "/user/market/products?page=1&pageSize=20"
        headers = @{ authentication = $userToken }
        contentType = ""
        bodyTemplate = ""
        bodyMode = "none"
        warmupRequests = $effectiveWarmupRequests
        measuredRequests = $effectiveMeasuredRequests
        concurrency = $effectiveConcurrency
    },
    [pscustomobject]@{
        name = "payment_callback"
        chain = "PaymentCallback"
        method = "POST"
        uri = $BaseUrl + "/payment/callback"
        headers = @{}
        contentType = "application/json"
        bodyTemplate = ('{"channel":"mock","orderNo":"' + $effectiveOrderNo + '","tradeNo":"__TRADE_NO__","amount":' + $effectiveOrderAmount + ',"status":"SUCCESS","timestamp":__TIMESTAMP__,"sign":"day19-perf-sign"}')
        bodyMode = "callback"
        warmupRequests = $effectiveWarmupRequests
        measuredRequests = $effectiveMeasuredRequests
        concurrency = $effectiveConcurrency
    }
)

$scenarioOutputs = @()
$interviewSummary = @()

foreach ($sc in $scenarios) {
    [void](Assert-ServiceAlive -Reason ("before warmup: " + $sc.name))
    $null = Invoke-ScenarioRun -ScenarioName $sc.name -Method $sc.method -Uri $sc.uri -Headers $sc.headers -ContentType $sc.contentType -BodyTemplate $sc.bodyTemplate -BodyMode $sc.bodyMode -TotalRequests $sc.warmupRequests -Concurrency $sc.concurrency -TimeoutSec $effectiveTimeoutSec -RoundNo 0 -Phase "warmup"

    $roundResults = @()
    for ($r = 1; $r -le $effectiveRounds; $r++) {
        [void](Assert-ServiceAlive -Reason ("before measure round " + $r + ": " + $sc.name))
        $run = Invoke-ScenarioRun -ScenarioName $sc.name -Method $sc.method -Uri $sc.uri -Headers $sc.headers -ContentType $sc.contentType -BodyTemplate $sc.bodyTemplate -BodyMode $sc.bodyMode -TotalRequests $sc.measuredRequests -Concurrency $sc.concurrency -TimeoutSec $effectiveTimeoutSec -RoundNo $r -Phase "measure"
        $roundResults += $run

        if ([int]$run.summary.connectivityFailureCount -gt 0) {
            [void](Assert-ServiceAlive -Reason ("post connectivity failure round " + $r + ": " + $sc.name))
        }
    }

    $aggregate = Merge-RoundSummaries -RunResults $roundResults

    $scenarioOutput = [pscustomobject]@{
        name = $sc.name
        chain = $sc.chain
        endpoint = ($sc.method + " " + ($sc.uri.Replace($BaseUrl, "")))
        method = $sc.method
        uri = $sc.uri
        params = [pscustomobject]@{
            profile = $Profile
            rounds = $effectiveRounds
            warmupRequests = $sc.warmupRequests
            measuredRequestsPerRound = $sc.measuredRequests
            totalMeasuredRequests = $sc.measuredRequests * $effectiveRounds
            concurrency = $sc.concurrency
            timeoutSec = $effectiveTimeoutSec
        }
        rounds = @($roundResults | ForEach-Object {
            [pscustomobject]@{
                round = $_.round
                startedAt = $_.startedAt
                endedAt = $_.endedAt
                totalRequests = $_.summary.totalRequests
                successRequests = $_.summary.successRequests
                failedRequests = $_.summary.failedRequests
                connectivityFailureCount = $_.summary.connectivityFailureCount
                errorRatePct = $_.summary.errorRatePct
                throughputRps = $_.summary.throughputRps
                p50Ms = $_.summary.p50Ms
                p95Ms = $_.summary.p95Ms
                minMs = $_.summary.minMs
                maxMs = $_.summary.maxMs
                durationSec = $_.summary.durationSec
                verdict = $_.summary.verdict
                errorGroupsTop10 = $_.summary.errorGroupsTop10
            }
        })
        aggregate = $aggregate
    }

    $scenarioOutputs += $scenarioOutput
    $interviewSummary += [pscustomobject]@{
        scenario = $scenarioOutput.name
        chain = $scenarioOutput.chain
        endpoint = $scenarioOutput.endpoint
        totalRequests = $scenarioOutput.aggregate.totalRequests
        p95Ms = $scenarioOutput.aggregate.p95Ms
        errorRatePct = $scenarioOutput.aggregate.errorRatePct
        throughputRps = $scenarioOutput.aggregate.throughputRps
        verdict = $scenarioOutput.aggregate.verdict
    }
}

$summary = [ordered]@{}
foreach ($s in $scenarioOutputs) {
    $summary[$s.name] = [ordered]@{
        chain = $s.chain
        endpoint = $s.endpoint
        p95Ms = $s.aggregate.p95Ms
        errorRatePct = $s.aggregate.errorRatePct
        throughputRps = $s.aggregate.throughputRps
        connectivityFailureCount = $s.aggregate.connectivityFailureCount
        totalMeasuredRequests = $s.aggregate.totalRequests
        verdict = $s.aggregate.verdict
    }
}

$result = [ordered]@{
    meta = [ordered]@{
        runAt = $now.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        script = $scriptPathForJson
        note = "Success = HTTP 2xx and Result.code=1; stop immediately if the service becomes unavailable"
    }
    config = [ordered]@{
        profile = $Profile
        rounds = $effectiveRounds
        warmupRequests = $effectiveWarmupRequests
        measuredRequests = $effectiveMeasuredRequests
        concurrency = $effectiveConcurrency
        timeoutSec = $effectiveTimeoutSec
    }
    preflight = $preflight
    scenarios = $scenarioOutputs
    interviewSummary = $interviewSummary
    summary = $summary
}

$result | ConvertTo-Json -Depth 20 | Set-Content -Path $outputPath -Encoding UTF8
Write-Output $outputPath
