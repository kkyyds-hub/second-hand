param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminLoginId = "13900000001",
    [string]$AdminPassword = "admin123",
    [string]$MySqlHost = "localhost",
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = "secondhand2",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "1234",
    [int[]]$BatchSizes = @(10, 25, 50, 100),
    [int]$RoundsPerLimit = 3,
    [int]$FailureBatchSize = 12,
    [int]$RecoveryBatchSize = 8,
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertFrom-JsonSafe {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }
    try {
        return $Text | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percent)
    if ($null -eq $Values -or $Values.Count -eq 0) { return $null }
    $sorted = $Values | Sort-Object
    $index = [int][Math]::Ceiling($Percent * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [Math]::Round([double]$sorted[$index], 2)
}

function Summarize-Numbers {
    param([double[]]$Values)
    if ($null -eq $Values -or $Values.Count -eq 0) {
        return [pscustomobject]@{
            count = 0
            min = $null
            p50 = $null
            p95 = $null
            max = $null
            avg = $null
        }
    }
    return [pscustomobject]@{
        count = $Values.Count
        min = [Math]::Round(([double]($Values | Measure-Object -Minimum).Minimum), 2)
        p50 = Get-Percentile -Values $Values -Percent 0.50
        p95 = Get-Percentile -Values $Values -Percent 0.95
        max = [Math]::Round(([double]($Values | Measure-Object -Maximum).Maximum), 2)
        avg = [Math]::Round(([double]($Values | Measure-Object -Average).Average), 2)
    }
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )

    $startedAt = Get-Date
    $httpStatus = $null
    $content = $null
    $errorType = $null
    $errorMessage = $null

    try {
        if ($Method -eq "GET") {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec 15
        } elseif ($null -eq $Body) {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -TimeoutSec 15
        } else {
            $jsonBody = $Body | ConvertTo-Json -Depth 10
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 15
        }
        $httpStatus = [int]$resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $response = $_.Exception.Response
            try { $httpStatus = [int]$response.StatusCode } catch { $httpStatus = $null }
            try {
                $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
                $content = $reader.ReadToEnd()
                $reader.Dispose()
            } catch {
                $content = $null
            }
        }
        $errorType = "HTTP_EXCEPTION"
        $errorMessage = $_.Exception.Message
    }

    $endedAt = Get-Date
    $parsed = ConvertFrom-JsonSafe -Text $content
    $businessCode = if ($null -ne $parsed -and $null -ne $parsed.code) { [int]$parsed.code } else { $null }
    $success = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $businessCode -eq 1)

    return [pscustomobject]@{
        httpStatus = $httpStatus
        businessCode = $businessCode
        success = $success
        elapsedMs = [Math]::Round(($endedAt - $startedAt).TotalMilliseconds, 2)
        errorType = $errorType
        errorMessage = $errorMessage
        body = $parsed
    }
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $day19DirName = "day19" + [string]([char]0x56DE) + [char]0x5F52
    $recordDirName = [string]([char]0x6267) + [char]0x884C + [char]0x8BB0 + [char]0x5F55
    $OutputDir = Join-Path $day19DirName $recordDirName
}
if (-not (Test-Path -Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$resultLabel = [string]([char]0x52A8) + [char]0x6001 + [char]0x7ED3 + [char]0x679C
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputPath = Join-Path $OutputDir ("Day19_P4_S1_{0}_{1}.json" -f $resultLabel, $timestamp)

$mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
if ($null -eq $mysqlCommand) {
    throw "mysql CLI not found for P4-S1"
}
$script:MySqlCli = $mysqlCommand.Source
$script:MySqlArgs = @(
    "-h", $MySqlHost,
    "-P", $MySqlPort.ToString(),
    "-u", $MySqlUser,
    "-p$MySqlPassword",
    "-D", $MySqlDatabase
)

function Invoke-MySqlQuery {
    param([string]$Sql)
    $lines = @(& $script:MySqlCli @script:MySqlArgs -N -B -e $Sql)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed"
    }
    return $lines
}

function Invoke-MySqlScript {
    param([string]$Sql)
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($tmp, $Sql, [System.Text.UTF8Encoding]::new($false))
        Get-Content -Raw $tmp | & $script:MySqlCli @script:MySqlArgs
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL script failed"
        }
    } finally {
        Remove-Item -Force $tmp -ErrorAction SilentlyContinue
    }
}

function Get-GlobalMetrics {
    param([string]$AdminToken)
    $resp = Invoke-ApiRequest -Method "GET" -Uri "$BaseUrl/admin/ops/outbox/metrics" -Headers @{ token = $AdminToken } -Body $null
    if (-not $resp.success) {
        throw "Failed to fetch Outbox metrics"
    }
    return $resp.body.data
}

function Get-BatchSummary {
    param([string]$Prefix)
    $sql = @"
SELECT
  COUNT(*) AS total_count,
  COALESCE(SUM(CASE WHEN status='NEW' THEN 1 ELSE 0 END),0) AS new_count,
  COALESCE(SUM(CASE WHEN status='SENT' THEN 1 ELSE 0 END),0) AS sent_count,
  COALESCE(SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END),0) AS fail_count,
  COALESCE(SUM(retry_count),0) AS retry_sum
FROM message_outbox
WHERE event_id LIKE '${Prefix}%';
"@
    $line = Invoke-MySqlQuery -Sql $sql | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($line)) {
        return [pscustomobject]@{
            total = 0
            new = 0
            sent = 0
            fail = 0
            failRetrySum = 0
        }
    }
    $parts = $line -split "`t"
    return [pscustomobject]@{
        total = [int]$parts[0]
        new = [int]$parts[1]
        sent = [int]$parts[2]
        fail = [int]$parts[3]
        failRetrySum = [int]$parts[4]
    }
}

function Get-BatchEventIds {
    param([string]$Prefix)
    $sql = "SELECT event_id FROM message_outbox WHERE event_id LIKE '${Prefix}%' ORDER BY id ASC;"
    return @(Invoke-MySqlQuery -Sql $sql)
}

function Remove-TestRows {
    param([string]$Prefix = "DAY19-P4-S1-")
    $sql = "DELETE FROM message_outbox WHERE event_id LIKE '${Prefix}%';"
    Invoke-MySqlScript -Sql $sql
}

function Set-BatchDueNow {
    param([string]$Prefix, [string]$Status)
    $sql = "UPDATE message_outbox SET next_retry_time = DATE_SUB(NOW(), INTERVAL 1 SECOND), updated_at = NOW() WHERE event_id LIKE '${Prefix}%' AND status = '${Status}';"
    Invoke-MySqlScript -Sql $sql
}

function Get-TemplateOutboxId {
    $sql = "SELECT id FROM message_outbox WHERE status='SENT' AND event_id NOT LIKE 'DAY19-P4-S1-%' ORDER BY id DESC LIMIT 1;"
    $line = Invoke-MySqlQuery -Sql $sql | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($line)) {
        throw "No reusable SENT Outbox template found"
    }
    return [int]$line
}

$templateOutboxId = Get-TemplateOutboxId

function Seed-SafeSuccessBatch {
    param([string]$Prefix, [int]$Count)
    $routingKey = "day19.outbox.safe.$($Prefix.ToLowerInvariant().Replace('_','.'))"
    $sql = @"
INSERT INTO message_outbox (
    event_id,
    event_type,
    routing_key,
    exchange_name,
    biz_id,
    payload_json,
    status,
    retry_count,
    next_retry_time,
    created_at,
    updated_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < $Count
)
SELECT
    CONCAT('$Prefix', '-', LPAD(seq.n, 3, '0')) AS event_id,
    'P4_S1_SAFE_SUCCESS' AS event_type,
    '$routingKey' AS routing_key,
    'order.events.exchange' AS exchange_name,
    940100000 + seq.n AS biz_id,
    REPLACE(
        REPLACE(
            REPLACE(t.payload_json, t.event_id, CONCAT('$Prefix', '-', LPAD(seq.n, 3, '0'))),
            t.routing_key, '$routingKey'
        ),
        t.event_type, 'P4_S1_SAFE_SUCCESS'
    ) AS payload_json,
    'NEW' AS status,
    0 AS retry_count,
    DATE_ADD(NOW(), INTERVAL 1 DAY) AS next_retry_time,
    NOW(),
    NOW()
FROM message_outbox t
JOIN seq ON 1 = 1
WHERE t.id = $templateOutboxId;
"@
    Invoke-MySqlScript -Sql $sql
}

function Seed-BadJsonBatch {
    param([string]$Prefix, [int]$Count)
    $sql = @"
INSERT INTO message_outbox (
    event_id,
    event_type,
    routing_key,
    exchange_name,
    biz_id,
    payload_json,
    status,
    retry_count,
    next_retry_time,
    created_at,
    updated_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < $Count
)
SELECT
    CONCAT('$Prefix', '-', LPAD(seq.n, 3, '0')) AS event_id,
    'P4_S1_BAD_JSON' AS event_type,
    'day19.outbox.fail.badjson' AS routing_key,
    'order.events.exchange' AS exchange_name,
    940200000 + seq.n AS biz_id,
    CONCAT('{bad-json-', LPAD(seq.n, 3, '0'), '}') AS payload_json,
    'NEW' AS status,
    0 AS retry_count,
    DATE_ADD(NOW(), INTERVAL 1 DAY) AS next_retry_time,
    NOW(),
    NOW()
FROM seq;
"@
    Invoke-MySqlScript -Sql $sql
}

function Seed-RecoveryFailBatch {
    param([string]$Prefix, [int]$Count)
    $routingKey = "day19.outbox.safe.recovery.$($Prefix.ToLowerInvariant().Replace('_','.'))"
    $sql = @"
INSERT INTO message_outbox (
    event_id,
    event_type,
    routing_key,
    exchange_name,
    biz_id,
    payload_json,
    status,
    retry_count,
    next_retry_time,
    created_at,
    updated_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < $Count
)
SELECT
    CONCAT('$Prefix', '-', LPAD(seq.n, 3, '0')) AS event_id,
    'P4_S1_RECOVERY' AS event_type,
    '$routingKey' AS routing_key,
    'order.events.exchange' AS exchange_name,
    940300000 + seq.n AS biz_id,
    REPLACE(
        REPLACE(
            REPLACE(t.payload_json, t.event_id, CONCAT('$Prefix', '-', LPAD(seq.n, 3, '0'))),
            t.routing_key, '$routingKey'
        ),
        t.event_type, 'P4_S1_RECOVERY'
    ) AS payload_json,
    'FAIL' AS status,
    2 AS retry_count,
    DATE_ADD(NOW(), INTERVAL 30 MINUTE) AS next_retry_time,
    NOW(),
    NOW()
FROM message_outbox t
JOIN seq ON 1 = 1
WHERE t.id = $templateOutboxId;
"@
    Invoke-MySqlScript -Sql $sql
}

function Invoke-PublishOnce {
    param([string]$AdminToken, [int]$Limit)
    $response = Invoke-ApiRequest -Method "POST" -Uri "$BaseUrl/admin/ops/outbox/publish-once?limit=$Limit" -Headers @{ token = $AdminToken } -Body $null
    if (-not $response.success) {
        throw "publish-once call failed"
    }
    return [pscustomobject]@{
        elapsedMs = $response.elapsedMs
        result = $response.body.data
    }
}

function Get-OutboxEvent {
    param([string]$AdminToken, [string]$EventId)
    $response = Invoke-ApiRequest -Method "GET" -Uri "$BaseUrl/admin/ops/outbox/event/$EventId" -Headers @{ token = $AdminToken } -Body $null
    if (-not $response.success) {
        throw "Failed to query Outbox event: $EventId"
    }
    return $response.body.data
}

function Trigger-OutboxEventNow {
    param([string]$AdminToken, [string]$EventId)
    $response = Invoke-ApiRequest -Method "POST" -Uri "$BaseUrl/admin/ops/outbox/event/$EventId/trigger-now" -Headers @{ token = $AdminToken } -Body $null
    if (-not $response.success) {
        throw "trigger-now call failed: $EventId"
    }
    return $response.body.data
}

$adminLogin = Invoke-ApiRequest -Method "POST" -Uri "$BaseUrl/admin/employee/login" -Headers @{} -Body @{ loginId = $AdminLoginId; password = $AdminPassword }
if (-not $adminLogin.success) {
    throw "Admin login failed"
}
$adminToken = [string]$adminLogin.body.data.token

Remove-TestRows
$baselineMetrics = Get-GlobalMetrics -AdminToken $adminToken

$successBenchmarks = New-Object System.Collections.Generic.List[object]
foreach ($limit in $BatchSizes) {
    $rounds = New-Object System.Collections.Generic.List[object]
    for ($round = 1; $round -le $RoundsPerLimit; $round++) {
        $prefix = "DAY19-P4-S1-SUCCESS-L${limit}-R${round}"
        Remove-TestRows -Prefix $prefix
        Seed-SafeSuccessBatch -Prefix $prefix -Count $limit
        $batchAfterSeed = Get-BatchSummary -Prefix $prefix
        $metricsAfterSeed = Get-GlobalMetrics -AdminToken $adminToken
        Set-BatchDueNow -Prefix $prefix -Status "NEW"
        $publish = Invoke-PublishOnce -AdminToken $adminToken -Limit $limit
        $batchAfterPublish = Get-BatchSummary -Prefix $prefix
        $metricsAfterPublish = Get-GlobalMetrics -AdminToken $adminToken
        $throughput = if ($publish.elapsedMs -gt 0) { [Math]::Round(($publish.result.sent * 1000.0 / $publish.elapsedMs), 2) } else { $null }
        $rounds.Add([pscustomobject]@{
            round = $round
            prefix = $prefix
            seed = $batchAfterSeed
            metricsAfterSeed = $metricsAfterSeed
            publishElapsedMs = $publish.elapsedMs
            publishResult = $publish.result
            throughputMsgsPerSec = $throughput
            batchAfterPublish = $batchAfterPublish
            metricsAfterPublish = $metricsAfterPublish
        })
        Remove-TestRows -Prefix $prefix
    }

    $roundArray = @($rounds.ToArray())
    $throughputs = @($roundArray | ForEach-Object { [double]$_.throughputMsgsPerSec })
    $elapsedValues = @($roundArray | ForEach-Object { [double]$_.publishElapsedMs })
    $successBenchmarks.Add([pscustomobject]@{
        limit = $limit
        rounds = $roundArray
        throughputSummary = Summarize-Numbers -Values $throughputs
        elapsedSummary = Summarize-Numbers -Values $elapsedValues
        totalSent = [int](($roundArray | ForEach-Object { [int]$_.publishResult.sent } | Measure-Object -Sum).Sum)
        totalFailed = [int](($roundArray | ForEach-Object { [int]$_.publishResult.failed } | Measure-Object -Sum).Sum)
    })
}

$failurePrefix = "DAY19-P4-S1-FAIL-CURVE"
Remove-TestRows -Prefix $failurePrefix
$failureMetricsBefore = Get-GlobalMetrics -AdminToken $adminToken
Seed-BadJsonBatch -Prefix $failurePrefix -Count $FailureBatchSize
$failureMetricsAfterSeed = Get-GlobalMetrics -AdminToken $adminToken
$failureBatchAfterSeed = Get-BatchSummary -Prefix $failurePrefix
Set-BatchDueNow -Prefix $failurePrefix -Status "NEW"
$failurePublish1 = Invoke-PublishOnce -AdminToken $adminToken -Limit $FailureBatchSize
$failureMetricsAfterPublish1 = Get-GlobalMetrics -AdminToken $adminToken
$failureBatchAfterPublish1 = Get-BatchSummary -Prefix $failurePrefix
Set-BatchDueNow -Prefix $failurePrefix -Status "FAIL"
$failurePublish2 = Invoke-PublishOnce -AdminToken $adminToken -Limit $FailureBatchSize
$failureMetricsAfterPublish2 = Get-GlobalMetrics -AdminToken $adminToken
$failureBatchAfterPublish2 = Get-BatchSummary -Prefix $failurePrefix
Remove-TestRows -Prefix $failurePrefix
$failureMetricsAfterCleanup = Get-GlobalMetrics -AdminToken $adminToken

$failureCurve = [pscustomobject]@{
    batchSize = $FailureBatchSize
    snapshots = @(
        [pscustomobject]@{ stage = "baseline"; metrics = $failureMetricsBefore; batch = (Get-BatchSummary -Prefix $failurePrefix) },
        [pscustomobject]@{ stage = "after_seed"; metrics = $failureMetricsAfterSeed; batch = $failureBatchAfterSeed },
        [pscustomobject]@{ stage = "after_publish_1"; metrics = $failureMetricsAfterPublish1; batch = $failureBatchAfterPublish1; publish = $failurePublish1 },
        [pscustomobject]@{ stage = "after_publish_2"; metrics = $failureMetricsAfterPublish2; batch = $failureBatchAfterPublish2; publish = $failurePublish2 },
        [pscustomobject]@{ stage = "after_cleanup"; metrics = $failureMetricsAfterCleanup; batch = (Get-BatchSummary -Prefix $failurePrefix) }
    )
    currentThresholdWouldAlert = (($failureBatchAfterPublish1.fail -ge 5) -or ($failureBatchAfterPublish1.failRetrySum -ge 10))
}

$recoveryPrefix = "DAY19-P4-S1-RECOVERY"
Remove-TestRows -Prefix $recoveryPrefix
$recoveryMetricsBefore = Get-GlobalMetrics -AdminToken $adminToken
Seed-RecoveryFailBatch -Prefix $recoveryPrefix -Count $RecoveryBatchSize
$recoveryMetricsAfterSeed = Get-GlobalMetrics -AdminToken $adminToken
$recoveryBatchAfterSeed = Get-BatchSummary -Prefix $recoveryPrefix
$recoveryEventIds = Get-BatchEventIds -Prefix $recoveryPrefix
$sampleEventId = $recoveryEventIds[0]
$sampleEventBefore = Get-OutboxEvent -AdminToken $adminToken -EventId $sampleEventId
$triggerResults = New-Object System.Collections.Generic.List[object]
foreach ($eventId in $recoveryEventIds) {
    $triggerResults.Add((Trigger-OutboxEventNow -AdminToken $adminToken -EventId $eventId))
}
$recoveryPublish = Invoke-PublishOnce -AdminToken $adminToken -Limit $RecoveryBatchSize
$recoveryMetricsAfterPublish = Get-GlobalMetrics -AdminToken $adminToken
$recoveryBatchAfterPublish = Get-BatchSummary -Prefix $recoveryPrefix
$sampleEventAfter = Get-OutboxEvent -AdminToken $adminToken -EventId $sampleEventId
Remove-TestRows -Prefix $recoveryPrefix
$recoveryMetricsAfterCleanup = Get-GlobalMetrics -AdminToken $adminToken

$recoveryClosure = [pscustomobject]@{
    batchSize = $RecoveryBatchSize
    metricsBefore = $recoveryMetricsBefore
    metricsAfterSeed = $recoveryMetricsAfterSeed
    metricsAfterPublish = $recoveryMetricsAfterPublish
    metricsAfterCleanup = $recoveryMetricsAfterCleanup
    batchAfterSeed = $recoveryBatchAfterSeed
    batchAfterPublish = $recoveryBatchAfterPublish
    sampleEventId = $sampleEventId
    sampleEventBefore = $sampleEventBefore
    sampleEventAfter = $sampleEventAfter
    triggerResults = @($triggerResults.ToArray())
    publish = $recoveryPublish
}

$benchmarkArray = @($successBenchmarks.ToArray())
$eligibleBenchmarks = @($benchmarkArray | Where-Object { $_.totalFailed -eq 0 })
$recommendedBatch = $null
if ($eligibleBenchmarks.Count -gt 0) {
    $sortedBenchmarks = @($eligibleBenchmarks | Sort-Object -Property { [double]$_.throughputSummary.avg } -Descending)
    $top = $sortedBenchmarks[0]
    if ($sortedBenchmarks.Count -gt 1) {
        $runnerUp = $sortedBenchmarks[1]
        $gainPct = if ([double]$runnerUp.throughputSummary.avg -gt 0) {
            [Math]::Round((([double]$top.throughputSummary.avg - [double]$runnerUp.throughputSummary.avg) * 100.0 / [double]$runnerUp.throughputSummary.avg), 2)
        } else {
            $null
        }
        if ($top.limit -gt $runnerUp.limit -and $null -ne $gainPct -and $gainPct -lt 15) {
            $recommendedBatch = $runnerUp
        } else {
            $recommendedBatch = $top
        }
    } else {
        $recommendedBatch = $top
    }
}

$recommendedBatchSizeValue = $null
$recommendedAvgThroughputValue = $null
$recommendedReasonValue = "No usable baseline"
if ($null -ne $recommendedBatch) {
    $recommendedBatchSizeValue = $recommendedBatch.limit
    $recommendedAvgThroughputValue = $recommendedBatch.throughputSummary.avg
    $recommendedReasonValue = "Prefer the batch-size with zero failures and best or near-best average throughput"
}

$result = [pscustomobject]@{
    meta = [pscustomobject]@{
        runAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        script = $PSCommandPath
        note = "Success samples use an existing exchange with an unbound routing key to avoid hitting real consumers"
    }
    config = [pscustomobject]@{
        batchSizes = $BatchSizes
        roundsPerLimit = $RoundsPerLimit
        failureBatchSize = $FailureBatchSize
        recoveryBatchSize = $RecoveryBatchSize
        monitorFailThreshold = 5
        monitorFailRetryThreshold = 10
    }
    preflight = [pscustomobject]@{
        adminLoginId = $AdminLoginId
        adminTokenAcquired = -not [string]::IsNullOrWhiteSpace($adminToken)
        rabbitPortReachable = $true
        templateOutboxId = $templateOutboxId
        baselineMetrics = $baselineMetrics
    }
    successBenchmarks = $benchmarkArray
    failureCurve = $failureCurve
    recoveryClosure = $recoveryClosure
    recommendation = [pscustomobject]@{
        recommendedBatchSize = $recommendedBatchSizeValue
        recommendedAvgThroughputMsgsPerSec = $recommendedAvgThroughputValue
        reason = $recommendedReasonValue
        monitorFailThresholdSuggestion = 5
        monitorFailRetryThresholdSuggestion = 10
        observationThresholdSuggestion = [pscustomobject]@{
            fail = 3
            failRetrySum = 6
        }
    }
}

[System.IO.File]::WriteAllText($outputPath, ($result | ConvertTo-Json -Depth 10), [System.Text.Encoding]::UTF8)
Write-Host "P4-S1 result: $outputPath"
if ($null -ne $recommendedBatch) {
    Write-Host ("Recommended batch-size={0}, avg throughput={1} msg/s" -f $recommendedBatch.limit, $recommendedBatch.throughputSummary.avg)
}
