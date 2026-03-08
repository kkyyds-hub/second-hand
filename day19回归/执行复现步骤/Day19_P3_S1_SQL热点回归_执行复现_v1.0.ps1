param(
    [string]$MySqlExe = "mysql",
    [string]$DbHost = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = "1234",
    [string]$Database = "secondhand2",
    [string]$OutputDir = "day19回归/执行记录",
    [string]$DumpPath = "c:\\Users\\kk\\Desktop\\_localhost__3_-2026_03_06_10_33_49-dump.sql"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-MySqlLines {
    param(
        [Parameter(Mandatory = $true)][string]$Sql
    )

    $args = @(
        "--host=$DbHost",
        "--port=$Port",
        "--user=$User",
        "--password=$Password",
        "--database=$Database",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--execute=$Sql"
    )

    $tmpErr = New-TemporaryFile
    try {
        $oldEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $output = & $MySqlExe @args 2> $tmpErr
        $ErrorActionPreference = $oldEap
        $code = $LASTEXITCODE
        $stderr = @()
        if (Test-Path $tmpErr) {
            $stderr = Get-Content -Encoding UTF8 $tmpErr
        }

        if ($code -ne 0) {
            throw "MySQL command failed ($code). SQL:`n$Sql`nSTDERR:`n$($stderr -join "`n")"
        }

        return @($output | Where-Object {
                $_ -notmatch "Using a password on the command line interface can be insecure"
            })
    } finally {
        $ErrorActionPreference = "Stop"
        if (Test-Path $tmpErr) {
            Remove-Item -Force $tmpErr -ErrorAction SilentlyContinue
        }
    }
}

function Invoke-MySqlText {
    param(
        [Parameter(Mandatory = $true)][string]$Sql
    )
    $lines = Invoke-MySqlLines -Sql $Sql
    return ($lines -join "`n")
}

function Run-Explain {
    param(
        [Parameter(Mandatory = $true)][string]$Sql
    )
    return Invoke-MySqlText -Sql ("EXPLAIN " + $Sql)
}

function Run-ExplainAnalyze {
    param(
        [Parameter(Mandatory = $true)][string]$Sql
    )
    return Invoke-MySqlText -Sql ("EXPLAIN ANALYZE " + $Sql)
}

if (-not (Test-Path $OutputDir)) {
    New-Item -Path $OutputDir -ItemType Directory -Force | Out-Null
}

$now = Get-Date
$ts = $now.ToString("yyyy-MM-dd_HH-mm-ss")
$outPath = Join-Path $OutputDir ("Day19_P3_S1_EXPLAIN证据_" + $ts + ".json")

$dbNow = Invoke-MySqlText -Sql "SELECT NOW(), DATABASE();"

$countsSql = @"
SELECT 'message_outbox' AS t, COUNT(*) AS c FROM message_outbox
UNION ALL SELECT 'order_ship_timeout_task', COUNT(*) FROM order_ship_timeout_task
UNION ALL SELECT 'order_refund_task', COUNT(*) FROM order_refund_task
UNION ALL SELECT 'order_ship_reminder_task', COUNT(*) FROM order_ship_reminder_task
UNION ALL SELECT 'orders', COUNT(*) FROM orders;
"@
$countLines = Invoke-MySqlLines -Sql $countsSql
$tableCardinality = [ordered]@{}
foreach ($line in $countLines) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 2) {
        $tableCardinality[$parts[0]] = [int64]$parts[1]
    }
}

$sampleSql = @"
SELECT COALESCE((SELECT event_id FROM message_outbox ORDER BY id DESC LIMIT 1),'') AS event_id,
       COALESCE((SELECT order_id FROM order_ship_timeout_task ORDER BY id DESC LIMIT 1),0) AS timeout_order_id,
       COALESCE((SELECT order_id FROM order_refund_task ORDER BY id DESC LIMIT 1),0) AS refund_order_id,
       COALESCE((SELECT order_id FROM order_ship_reminder_task ORDER BY id DESC LIMIT 1),0) AS reminder_order_id,
       COALESCE((SELECT id FROM orders ORDER BY id DESC LIMIT 1),0) AS any_order_id,
       COALESCE((SELECT order_no FROM orders ORDER BY id DESC LIMIT 1),'') AS any_order_no,
       COALESCE((SELECT status FROM order_ship_timeout_task ORDER BY id DESC LIMIT 1),'PENDING') AS timeout_status,
       COALESCE((SELECT status FROM order_refund_task ORDER BY id DESC LIMIT 1),'FAILED') AS refund_status,
       COALESCE((SELECT status FROM order_ship_reminder_task ORDER BY id DESC LIMIT 1),'FAILED') AS reminder_status;
"@
$sampleLine = (Invoke-MySqlLines -Sql $sampleSql | Select-Object -First 1)
$sampleParts = $sampleLine -split "`t"
$sample = [ordered]@{
    eventId = if ($sampleParts.Count -ge 1) { $sampleParts[0] } else { "" }
    timeoutOrderId = if ($sampleParts.Count -ge 2) { [int64]$sampleParts[1] } else { 0 }
    refundOrderId = if ($sampleParts.Count -ge 3) { [int64]$sampleParts[2] } else { 0 }
    reminderOrderId = if ($sampleParts.Count -ge 4) { [int64]$sampleParts[3] } else { 0 }
    anyOrderId = if ($sampleParts.Count -ge 5) { [int64]$sampleParts[4] } else { 0 }
    anyOrderNo = if ($sampleParts.Count -ge 6) { $sampleParts[5] } else { "" }
    timeoutStatus = if ($sampleParts.Count -ge 7) { $sampleParts[6] } else { "PENDING" }
    refundStatus = if ($sampleParts.Count -ge 8) { $sampleParts[7] } else { "FAILED" }
    reminderStatus = if ($sampleParts.Count -ge 9) { $sampleParts[8] } else { "FAILED" }
}

$indexes = [ordered]@{}
foreach ($t in @("message_outbox", "order_ship_timeout_task", "order_refund_task", "order_ship_reminder_task", "orders")) {
    $indexes[$t] = Invoke-MySqlText -Sql ("SHOW INDEX FROM " + $t)
}

$sqlMap = [ordered]@{}
$sqlMap["outbox_listPending"] = @"
SELECT t.id,t.status,t.next_retry_time,t.exchange_name
FROM (
  (SELECT id,status,next_retry_time,exchange_name
   FROM message_outbox
   WHERE status='NEW'
     AND (next_retry_time IS NULL OR next_retry_time <= NOW())
     AND exchange_name NOT IN ('bad.exchange')
   ORDER BY id ASC
   LIMIT 200)
  UNION ALL
  (SELECT id,status,next_retry_time,exchange_name
   FROM message_outbox
   WHERE status='FAIL'
     AND (next_retry_time IS NULL OR next_retry_time <= NOW())
     AND exchange_name NOT IN ('bad.exchange')
   ORDER BY id ASC
   LIMIT 200)
) t
ORDER BY t.id ASC
LIMIT 200
"@
$sqlMap["outbox_countFail"] = "SELECT COUNT(*) FROM message_outbox WHERE status='FAIL' AND exchange_name NOT IN ('bad.exchange')"
$sqlMap["outbox_sumRetryFail"] = "SELECT COALESCE(SUM(retry_count),0) FROM message_outbox WHERE status='FAIL' AND exchange_name NOT IN ('bad.exchange')"
$sqlMap["outbox_selectByEventId"] = "SELECT id,event_id,status,retry_count,next_retry_time FROM message_outbox WHERE event_id='" + $sample.eventId + "' LIMIT 1"
$sqlMap["outbox_triggerNowByEventId_update"] = "UPDATE message_outbox SET next_retry_time=NULL, updated_at=NOW() WHERE event_id='" + $sample.eventId + "' AND status IN ('NEW','FAIL')"

$sqlMap["shipTimeout_listDuePending"] = "SELECT id,order_id,deadline_time,status,retry_count,next_retry_time FROM order_ship_timeout_task WHERE status='PENDING' AND deadline_time<=NOW() AND (next_retry_time IS NULL OR next_retry_time<=NOW()) ORDER BY deadline_time ASC, id ASC LIMIT 200"
$sqlMap["shipTimeout_listForAdmin_byStatus"] = "SELECT id,order_id,status,deadline_time,next_retry_time FROM order_ship_timeout_task WHERE status='" + $sample.timeoutStatus + "' ORDER BY id DESC LIMIT 0,100"
$sqlMap["shipTimeout_listForAdmin_byOrderId"] = "SELECT id,order_id,status,deadline_time,next_retry_time FROM order_ship_timeout_task WHERE order_id=" + $sample.timeoutOrderId + " ORDER BY id DESC LIMIT 0,100"
$sqlMap["shipTimeout_countForAdmin_byStatus"] = "SELECT COUNT(1) FROM order_ship_timeout_task WHERE status='" + $sample.timeoutStatus + "'"

$sqlMap["refund_listRunnable"] = "SELECT id,order_id,refund_type,status,retry_count,next_retry_time FROM order_refund_task WHERE status IN ('PENDING','FAILED') AND (next_retry_time IS NULL OR next_retry_time<=NOW()) ORDER BY id ASC LIMIT 200"
$sqlMap["refund_listForAdmin_byStatus"] = "SELECT id,order_id,refund_type,status,next_retry_time FROM order_refund_task WHERE status='" + $sample.refundStatus + "' ORDER BY id DESC LIMIT 0,100"
$sqlMap["refund_listForAdmin_byOrderId"] = "SELECT id,order_id,refund_type,status,next_retry_time FROM order_refund_task WHERE order_id=" + $sample.refundOrderId + " ORDER BY id DESC LIMIT 0,100"
$sqlMap["refund_countForAdmin_byStatus"] = "SELECT COUNT(1) FROM order_refund_task WHERE status='" + $sample.refundStatus + "'"

$sqlMap["reminder_markRunningBatch_update"] = "UPDATE order_ship_reminder_task SET status='RUNNING', running_at=NOW(), update_time=NOW() WHERE status IN ('PENDING','FAILED') AND remind_time<=NOW() ORDER BY remind_time ASC, id ASC LIMIT 200"
$sqlMap["reminder_listStaleRunning"] = "SELECT id,order_id,status,running_at,remind_time,retry_count FROM order_ship_reminder_task WHERE status='RUNNING' AND running_at <= DATE_SUB(NOW(), INTERVAL 5 MINUTE) ORDER BY running_at ASC, id ASC LIMIT 200"
$sqlMap["reminder_listForAdmin_byStatus"] = "SELECT id,order_id,status,running_at,remind_time FROM order_ship_reminder_task WHERE status='" + $sample.reminderStatus + "' ORDER BY id DESC LIMIT 0,100"
$sqlMap["reminder_countForAdmin_byStatus"] = "SELECT COUNT(1) FROM order_ship_reminder_task WHERE status='" + $sample.reminderStatus + "'"

$sqlMap["adminOrders_list_byCreateTime"] = "SELECT o.id,o.order_no,o.status,o.create_time,o.pay_time FROM orders o WHERE o.status='paid' ORDER BY o.create_time DESC LIMIT 0,100"
$sqlMap["adminOrders_list_byPayTime"] = "SELECT o.id,o.order_no,o.status,o.create_time,o.pay_time FROM orders o WHERE o.status='paid' ORDER BY o.pay_time DESC LIMIT 0,100"
$sqlMap["order_selectByOrderNo"] = "SELECT id,order_no,status,pay_time,create_time FROM orders WHERE order_no='" + $sample.anyOrderNo + "' LIMIT 1"
$sqlMap["order_updateForPayByOrderNo"] = "UPDATE orders SET status='paid', pay_time=NOW(), update_time=NOW() WHERE order_no='" + $sample.anyOrderNo + "' AND status='pending'"

$explain = [ordered]@{}
foreach ($k in $sqlMap.Keys) {
    $explain[$k] = Run-Explain -Sql $sqlMap[$k]
}

$analyzeKeys = @(
    "outbox_countFail",
    "shipTimeout_listDuePending",
    "refund_listRunnable",
    "reminder_listStaleRunning",
    "adminOrders_list_byCreateTime"
)

$explainAnalyze = [ordered]@{}
foreach ($k in $analyzeKeys) {
    try {
        $explainAnalyze[$k] = Run-ExplainAnalyze -Sql $sqlMap[$k]
    } catch {
        $explainAnalyze[$k] = "ERROR: " + $_.Exception.Message
    }
}

$dumpMeta = $null
if (Test-Path $DumpPath) {
    $fi = Get-Item $DumpPath
    $dumpMeta = [ordered]@{
        path = $fi.FullName
        length = $fi.Length
        lastWriteTime = $fi.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
    }
}

$result = [ordered]@{
    generatedAt = $now.ToString("yyyy-MM-dd HH:mm:ss")
    dbNow = $dbNow
    db = $Database
    sourceDump = $dumpMeta
    script = "day19回归/执行复现步骤/Day19_P3_S1_SQL热点回归_执行复现_v1.0.ps1"
    tableCardinality = $tableCardinality
    sampledParams = $sample
    indexes = $indexes
    sql = $sqlMap
    explain = $explain
    explainAnalyze = $explainAnalyze
}

$result | ConvertTo-Json -Depth 20 | Set-Content -Path $outPath -Encoding UTF8
Write-Output $outPath
