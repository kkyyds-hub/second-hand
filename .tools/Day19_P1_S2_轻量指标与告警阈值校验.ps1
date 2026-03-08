param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$RecordDir = "",
    [string]$LogFile = "",

    [string]$AdminLoginId = "13900000001",
    [string]$AdminPassword = "admin123",

    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPassword = "1234",
    [string]$DbName = "secondhand2",

    [int]$SampleRounds = 3,
    [int]$SampleIntervalSeconds = 10,
    [int]$OutboxPublishLimit = 100,
    [int]$TaskRunLimit = 200
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Stage {
    param([string]$Message)
    Write-Host ("`n[Day19-P1-S2] {0}" -f $Message) -ForegroundColor Cyan
}

function Sanitize-Value {
    param([object]$Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [string]) { return $Value }
    if ($Value -is [System.Collections.IDictionary]) {
        $obj = @{}
        foreach ($k in $Value.Keys) {
            $key = [string]$k
            if ($key -match "(?i)token|password|secret|sign|authorization") { $obj[$key] = "<redacted>" }
            else { $obj[$key] = Sanitize-Value $Value[$k] }
        }
        return $obj
    }
    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        $arr = @()
        foreach ($item in $Value) { $arr += ,(Sanitize-Value $item) }
        return $arr
    }
    if ($Value -is [psobject]) {
        $obj = @{}
        foreach ($p in $Value.PSObject.Properties) {
            if ($p.Name -match "(?i)token|password|secret|sign|authorization") { $obj[$p.Name] = "<redacted>" }
            else { $obj[$p.Name] = Sanitize-Value $p.Value }
        }
        return $obj
    }
    return $Value
}

function Invoke-ApiWithTiming {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $ret = [ordered]@{
        name = $Name
        method = $Method
        url = $Url
        at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
        costMs = 0.0
        httpStatus = $null
        ok = $false
        response = $null
        error = $null
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        if ($null -ne $Body) {
            $jsonBody = $Body | ConvertTo-Json -Depth 20 -Compress
            $resp = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody
        } else {
            $resp = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers
        }
        $ret.httpStatus = 200
        $ret.ok = $true
        $ret.response = $resp
    } catch {
        $ret.error = $_.Exception.Message
        if ($_.Exception.Response) {
            try { $ret.httpStatus = [int]$_.Exception.Response.StatusCode } catch { $ret.httpStatus = -1 }
            try {
                $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $raw = $sr.ReadToEnd()
                $sr.Close()
                if ($raw) {
                    try { $ret.response = $raw | ConvertFrom-Json } catch { $ret.response = $raw }
                }
            } catch { }
        }
    } finally {
        $sw.Stop()
        $ret.costMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
    }
    return [pscustomobject]$ret
}

function Get-ApiCode { param([psobject]$r) if($null -eq $r -or $null -eq $r.response){return $null}; if($r.response.PSObject.Properties.Name -contains "code"){return $r.response.code}; return $null }
function Get-ApiData { param([psobject]$r) if($null -eq $r -or $null -eq $r.response){return $null}; if($r.response.PSObject.Properties.Name -contains "data"){return $r.response.data}; return $null }

function Invoke-DbScalar {
    param(
        [string]$Name,
        [string]$Sql,
        [string]$DbHost,
        [int]$DbPort,
        [string]$DbUser,
        [string]$DbPassword,
        [string]$DbName
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $ret = [ordered]@{ name=$Name; ok=$false; value=$null; raw=$null; costMs=0.0; error=$null }
    $oldPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $DbPassword
        $args = @("-h",$DbHost,"-P","$DbPort","-u",$DbUser,"-D",$DbName,"-N","-e",$Sql)
        $raw = & mysql @args 2>$null
        if ($LASTEXITCODE -ne 0) {
            $ret.error = ($raw | Out-String).Trim()
        } else {
            $line = (($raw | Out-String).Trim() -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1)
            $ret.raw = $line
            if ($null -ne $line -and $line -match "^-?\d+(\.\d+)?$") {
                $ret.value = [double]$line
                $ret.ok = $true
            } else {
                $ret.error = "unexpected_db_output: $line"
            }
        }
    } catch {
        $ret.error = $_.Exception.Message
    } finally {
        if ($null -eq $oldPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        else { $env:MYSQL_PWD = $oldPwd }
        $sw.Stop()
        $ret.costMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
    }
    return [pscustomobject]$ret
}

function Get-LevelByConsecutive {
    param(
        [object[]]$Values,
        [double]$WarnThreshold,
        [double]$ErrThreshold,
        [ValidateSet("ge", "lt")] [string]$Direction = "ge",
        [int]$Consecutive = 3
    )
    $vals = @($Values | Where-Object { $null -ne $_ })
    if ($vals.Count -lt $Consecutive) { return "NO_SAMPLE" }
    $tail = @($vals | Select-Object -Last $Consecutive)

    if ($Direction -eq "ge") {
        if ((@($tail | Where-Object { [double]$_ -ge $ErrThreshold }).Count) -eq $Consecutive) { return "ERROR" }
        if ((@($tail | Where-Object { [double]$_ -ge $WarnThreshold }).Count) -eq $Consecutive) { return "WARNING" }
    } else {
        if ((@($tail | Where-Object { [double]$_ -lt $ErrThreshold }).Count) -eq $Consecutive) { return "ERROR" }
        if ((@($tail | Where-Object { [double]$_ -lt $WarnThreshold }).Count) -eq $Consecutive) { return "WARNING" }
    }
    return "NORMAL"
}

function New-AlertResult {
    param(
        [string]$Id,
        [string]$Metric,
        [double]$WarnThreshold,
        [double]$ErrThreshold,
        [string]$Direction,
        [object[]]$Values,
        [string]$Flow
    )
    $level = Get-LevelByConsecutive -Values $Values -WarnThreshold $WarnThreshold -ErrThreshold $ErrThreshold -Direction $Direction -Consecutive 3
    $latest = $null
    $notNullVals = @($Values | Where-Object { $null -ne $_ })
    if ($notNullVals.Count -gt 0) { $latest = [double]($notNullVals | Select-Object -Last 1) }
    return [ordered]@{
        alertId = $Id
        metric = $Metric
        warn = $WarnThreshold
        err = $ErrThreshold
        direction = $Direction
        level = $level
        latest = $latest
        flow = $Flow
        values = $Values
    }
}

Write-Stage "Initialize output path"
if ([string]::IsNullOrWhiteSpace($RecordDir)) {
    $sep = [System.IO.Path]::DirectorySeparatorChar
    $day19Name = "day19" + [char]0x56DE + [char]0x5F52
    $recordName = [char]0x6267 + [char]0x884C + [char]0x8BB0 + [char]0x5F55
    $RecordDir = $day19Name + $sep + $recordName
}
if (-not (Test-Path $RecordDir)) { New-Item -ItemType Directory -Path $RecordDir -Force | Out-Null }

$runAt = Get-Date
$timestamp = $runAt.ToString("yyyy-MM-dd_HH-mm-ss")
$outputPath = Join-Path $RecordDir ("Day19_P1_S2_Dynamic_Result_{0}.json" -f $timestamp)
$notes = New-Object System.Collections.ArrayList
$rounds = New-Object System.Collections.ArrayList

$sqlCatalog = [ordered]@{
    outboxNew = "SELECT COUNT(*) FROM message_outbox WHERE status='NEW' AND exchange_name NOT IN ('bad.exchange');"
    outboxFail = "SELECT COUNT(*) FROM message_outbox WHERE status='FAIL' AND exchange_name NOT IN ('bad.exchange');"
    outboxFailRetrySum = "SELECT COALESCE(SUM(retry_count),0) FROM message_outbox WHERE status='FAIL' AND exchange_name NOT IN ('bad.exchange');"
    shipTimeoutBacklog = "SELECT COUNT(1) FROM order_ship_timeout_task WHERE status='PENDING' AND deadline_time<=NOW() AND (next_retry_time IS NULL OR next_retry_time<=NOW());"
    refundBacklog = "SELECT COUNT(1) FROM order_refund_task WHERE status IN ('PENDING','FAILED') AND (next_retry_time IS NULL OR next_retry_time<=NOW());"
    shipReminderBacklog = "SELECT COUNT(1) FROM order_ship_reminder_task WHERE status IN ('PENDING','FAILED') AND remind_time<=NOW();"
    taskHighRetryCount = "SELECT COALESCE(SUM(cnt),0) FROM (SELECT COUNT(*) AS cnt FROM order_ship_timeout_task WHERE retry_count>=3 UNION ALL SELECT COUNT(*) AS cnt FROM order_refund_task WHERE retry_count>=3 UNION ALL SELECT COUNT(*) AS cnt FROM order_ship_reminder_task WHERE retry_count>=3) t;"
    taskSevereRetryCount = "SELECT COALESCE(SUM(cnt),0) FROM (SELECT COUNT(*) AS cnt FROM order_ship_timeout_task WHERE retry_count>=5 UNION ALL SELECT COUNT(*) AS cnt FROM order_refund_task WHERE retry_count>=5 UNION ALL SELECT COUNT(*) AS cnt FROM order_ship_reminder_task WHERE retry_count>=5) t;"
}

$evidence = [ordered]@{
    meta = [ordered]@{
        runAt = $runAt.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        sampleRounds = $SampleRounds
        sampleIntervalSeconds = $SampleIntervalSeconds
        outboxPublishLimit = $OutboxPublishLimit
        taskRunLimit = $TaskRunLimit
        dbTarget = "${DbUser}@${DbHost}:$DbPort/$DbName"
        note = "sensitive fields redacted"
    }
    sqlCatalog = $sqlCatalog
    rounds = $rounds
    alerts = @()
    ops = [ordered]@{}
    logs = [ordered]@{}
    dod = [ordered]@{}
    notes = $notes
}

Write-Stage "Admin login"
$adminLogin = Invoke-ApiWithTiming -Name "admin_login" -Method "POST" -Url ($BaseUrl + "/admin/employee/login") -Body @{ loginId = $AdminLoginId; password = $AdminPassword }
$evidence.ops["adminLogin"] = Sanitize-Value $adminLogin
if ((Get-ApiCode $adminLogin) -ne 1) { throw "admin login failed" }
$token = [string](Get-ApiData $adminLogin).token
$headers = @{ token = $token }

Write-Stage "Sampling rounds"
for ($i = 1; $i -le $SampleRounds; $i++) {
    Write-Host ("- Round {0}/{1}" -f $i, $SampleRounds) -ForegroundColor DarkCyan
    $roundAt = Get-Date

    $dbOutboxNew = Invoke-DbScalar -Name "outbox_new" -Sql $sqlCatalog.outboxNew -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbOutboxFail = Invoke-DbScalar -Name "outbox_fail" -Sql $sqlCatalog.outboxFail -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbOutboxRetrySum = Invoke-DbScalar -Name "outbox_fail_retry_sum" -Sql $sqlCatalog.outboxFailRetrySum -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbShipTimeoutBacklog = Invoke-DbScalar -Name "ship_timeout_backlog" -Sql $sqlCatalog.shipTimeoutBacklog -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbRefundBacklog = Invoke-DbScalar -Name "refund_backlog" -Sql $sqlCatalog.refundBacklog -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbShipReminderBacklog = Invoke-DbScalar -Name "ship_reminder_backlog" -Sql $sqlCatalog.shipReminderBacklog -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbTaskHighRetry = Invoke-DbScalar -Name "task_high_retry_count" -Sql $sqlCatalog.taskHighRetryCount -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName
    $dbTaskSevRetry = Invoke-DbScalar -Name "task_severe_retry_count" -Sql $sqlCatalog.taskSevereRetryCount -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword -DbName $DbName

    $metricsApi = Invoke-ApiWithTiming -Name "outbox_metrics" -Method "GET" -Url ($BaseUrl + "/admin/ops/outbox/metrics") -Headers $headers
    $publishApi = Invoke-ApiWithTiming -Name "outbox_publish_once" -Method "POST" -Url ($BaseUrl + "/admin/ops/outbox/publish-once?limit=$OutboxPublishLimit") -Headers $headers
    $runShipTimeout = Invoke-ApiWithTiming -Name "task_run_ship_timeout" -Method "POST" -Url ($BaseUrl + "/admin/ops/tasks/ship-timeout/run-once?limit=$TaskRunLimit") -Headers $headers
    $runRefund = Invoke-ApiWithTiming -Name "task_run_refund" -Method "POST" -Url ($BaseUrl + "/admin/ops/tasks/refund/run-once?limit=$TaskRunLimit") -Headers $headers
    $runShipReminder = Invoke-ApiWithTiming -Name "task_run_ship_reminder" -Method "POST" -Url ($BaseUrl + "/admin/ops/tasks/ship-reminder/run-once?limit=$TaskRunLimit") -Headers $headers

    $publishData = Get-ApiData $publishApi
    $sent = 0; $failed = 0
    if ($null -ne $publishData) {
        if ($publishData.PSObject.Properties.Name -contains "sent") { $sent = [int]$publishData.sent }
        if ($publishData.PSObject.Properties.Name -contains "failed") { $failed = [int]$publishData.failed }
    }

    $outboxRate = $null
    if (($sent + $failed) -gt 0) { $outboxRate = [Math]::Round(($sent * 100.0) / ($sent + $failed), 2) }

    $runStSuccess = 0
    $runRfSuccess = 0
    $runSrSuccess = 0
    $runStData = Get-ApiData $runShipTimeout
    $runRfData = Get-ApiData $runRefund
    $runSrData = Get-ApiData $runShipReminder
    if ($null -ne $runStData -and ($runStData.PSObject.Properties.Name -contains "success")) { $runStSuccess = [int]$runStData.success }
    if ($null -ne $runRfData -and ($runRfData.PSObject.Properties.Name -contains "success")) { $runRfSuccess = [int]$runRfData.success }
    if ($null -ne $runSrData -and ($runSrData.PSObject.Properties.Name -contains "success")) { $runSrSuccess = [int]$runSrData.success }

    $stBacklogBefore = if ($dbShipTimeoutBacklog.ok) { [int]$dbShipTimeoutBacklog.value } else { 0 }
    $rfBacklogBefore = if ($dbRefundBacklog.ok) { [int]$dbRefundBacklog.value } else { 0 }
    $srBacklogBefore = if ($dbShipReminderBacklog.ok) { [int]$dbShipReminderBacklog.value } else { 0 }

    $stRate = $null
    $rfRate = $null
    $srRate = $null
    $stDen = [Math]::Min($TaskRunLimit, $stBacklogBefore)
    $rfDen = [Math]::Min($TaskRunLimit, $rfBacklogBefore)
    $srDen = [Math]::Min($TaskRunLimit, $srBacklogBefore)
    if ($stDen -gt 0) { $stRate = [Math]::Round(($runStSuccess * 100.0) / $stDen, 2) }
    if ($rfDen -gt 0) { $rfRate = [Math]::Round(($runRfSuccess * 100.0) / $rfDen, 2) }
    if ($srDen -gt 0) { $srRate = [Math]::Round(($runSrSuccess * 100.0) / $srDen, 2) }

    $taskRates = @(@($stRate, $rfRate, $srRate) | Where-Object { $null -ne $_ })
    $taskOverallRate = $null
    if ($taskRates.Count -gt 0) { $taskOverallRate = [double]($taskRates | Measure-Object -Minimum).Minimum }

    $roundObj = [ordered]@{
        round = $i
        at = $roundAt.ToString("yyyy-MM-dd HH:mm:ss")
        metrics = [ordered]@{
            outboxNew = if ($dbOutboxNew.ok) { [int]$dbOutboxNew.value } else { $null }
            outboxFail = if ($dbOutboxFail.ok) { [int]$dbOutboxFail.value } else { $null }
            outboxBacklog = if ($dbOutboxNew.ok -and $dbOutboxFail.ok) { [int]$dbOutboxNew.value + [int]$dbOutboxFail.value } else { $null }
            outboxFailRetrySum = if ($dbOutboxRetrySum.ok) { [int]$dbOutboxRetrySum.value } else { $null }
            shipTimeoutBacklog = if ($dbShipTimeoutBacklog.ok) { [int]$dbShipTimeoutBacklog.value } else { $null }
            refundBacklog = if ($dbRefundBacklog.ok) { [int]$dbRefundBacklog.value } else { $null }
            shipReminderBacklog = if ($dbShipReminderBacklog.ok) { [int]$dbShipReminderBacklog.value } else { $null }
            taskHighRetryCount = if ($dbTaskHighRetry.ok) { [int]$dbTaskHighRetry.value } else { $null }
            taskSevereRetryCount = if ($dbTaskSevRetry.ok) { [int]$dbTaskSevRetry.value } else { $null }
            outboxPublishSuccessRate = $outboxRate
            shipTimeoutRunSuccessRate = $stRate
            refundRunSuccessRate = $rfRate
            shipReminderRunSuccessRate = $srRate
            taskRunSuccessRate = $taskOverallRate
        }
        apis = [ordered]@{
            outboxMetrics = Sanitize-Value $metricsApi
            outboxPublishOnce = Sanitize-Value $publishApi
            runShipTimeout = Sanitize-Value $runShipTimeout
            runRefund = Sanitize-Value $runRefund
            runShipReminder = Sanitize-Value $runShipReminder
        }
        db = [ordered]@{
            outboxNew = Sanitize-Value $dbOutboxNew
            outboxFail = Sanitize-Value $dbOutboxFail
            outboxFailRetrySum = Sanitize-Value $dbOutboxRetrySum
            shipTimeoutBacklog = Sanitize-Value $dbShipTimeoutBacklog
            refundBacklog = Sanitize-Value $dbRefundBacklog
            shipReminderBacklog = Sanitize-Value $dbShipReminderBacklog
            taskHighRetryCount = Sanitize-Value $dbTaskHighRetry
            taskSevereRetryCount = Sanitize-Value $dbTaskSevRetry
        }
    }
    [void]$rounds.Add($roundObj)

    foreach ($dbProbe in @($dbOutboxNew, $dbOutboxFail, $dbOutboxRetrySum, $dbShipTimeoutBacklog, $dbRefundBacklog, $dbShipReminderBacklog, $dbTaskHighRetry, $dbTaskSevRetry)) {
        if (-not $dbProbe.ok) { [void]$notes.Add("DB probe failed: $($dbProbe.name) -> $($dbProbe.error)") }
    }

    if ($i -lt $SampleRounds -and $SampleIntervalSeconds -gt 0) { Start-Sleep -Seconds $SampleIntervalSeconds }
}

Write-Stage "Evaluate alerts"
$valsOutboxBacklog = @($rounds | ForEach-Object { $_.metrics.outboxBacklog })
$valsStBacklog = @($rounds | ForEach-Object { $_.metrics.shipTimeoutBacklog })
$valsRfBacklog = @($rounds | ForEach-Object { $_.metrics.refundBacklog })
$valsSrBacklog = @($rounds | ForEach-Object { $_.metrics.shipReminderBacklog })
$valsOutboxFail = @($rounds | ForEach-Object { $_.metrics.outboxFail })
$valsOutboxRetrySum = @($rounds | ForEach-Object { $_.metrics.outboxFailRetrySum })
$valsTaskHighRetry = @($rounds | ForEach-Object { $_.metrics.taskHighRetryCount })
$valsTaskSevRetry = @($rounds | ForEach-Object { $_.metrics.taskSevereRetryCount })
$valsOutboxRate = @($rounds | ForEach-Object { $_.metrics.outboxPublishSuccessRate })
$valsTaskRate = @($rounds | ForEach-Object { $_.metrics.taskRunSuccessRate })

$alerts = @(
    (New-AlertResult -Id "BKL-OUTBOX" -Metric "outboxBacklog" -WarnThreshold 100 -ErrThreshold 300 -Direction "ge" -Values $valsOutboxBacklog -Flow "FLOW-A"),
    (New-AlertResult -Id "BKL-ST" -Metric "shipTimeoutBacklog" -WarnThreshold 200 -ErrThreshold 600 -Direction "ge" -Values $valsStBacklog -Flow "FLOW-A"),
    (New-AlertResult -Id "BKL-RF" -Metric "refundBacklog" -WarnThreshold 200 -ErrThreshold 600 -Direction "ge" -Values $valsRfBacklog -Flow "FLOW-A"),
    (New-AlertResult -Id "BKL-SR" -Metric "shipReminderBacklog" -WarnThreshold 200 -ErrThreshold 600 -Direction "ge" -Values $valsSrBacklog -Flow "FLOW-A"),
    (New-AlertResult -Id "RETRY-OUTBOX-CNT" -Metric "outboxFailCount" -WarnThreshold 5 -ErrThreshold 10 -Direction "ge" -Values $valsOutboxFail -Flow "FLOW-B"),
    (New-AlertResult -Id "RETRY-OUTBOX-SUM" -Metric "outboxFailRetrySum" -WarnThreshold 10 -ErrThreshold 20 -Direction "ge" -Values $valsOutboxRetrySum -Flow "FLOW-B"),
    (New-AlertResult -Id "RETRY-TASK-HIGH" -Metric "taskHighRetryCount" -WarnThreshold 5 -ErrThreshold 15 -Direction "ge" -Values $valsTaskHighRetry -Flow "FLOW-B"),
    (New-AlertResult -Id "RETRY-TASK-SEV" -Metric "taskSevereRetryCount" -WarnThreshold 1 -ErrThreshold 5 -Direction "ge" -Values $valsTaskSevRetry -Flow "FLOW-B"),
    (New-AlertResult -Id "SR-OUTBOX" -Metric "outboxPublishSuccessRate" -WarnThreshold 95 -ErrThreshold 90 -Direction "lt" -Values $valsOutboxRate -Flow "FLOW-C"),
    (New-AlertResult -Id "SR-TASK" -Metric "taskRunSuccessRate" -WarnThreshold 90 -ErrThreshold 80 -Direction "lt" -Values $valsTaskRate -Flow "FLOW-C")
)
$evidence.alerts = $alerts

$evidence.ops["flowActions"] = [ordered]@{
    "FLOW-A" = @("POST /admin/ops/outbox/publish-once?limit=100","POST /admin/ops/tasks/ship-timeout/run-once?limit=200","POST /admin/ops/tasks/refund/run-once?limit=200","POST /admin/ops/tasks/ship-reminder/run-once?limit=200","if still high: trigger-now/reset")
    "FLOW-B" = @("query high retry samples by SQL","run trigger-now/reset on samples","run publish-once/run-once again","verify retry growth stops")
    "FLOW-C" = @("run 3 rounds for success rate","if below threshold run compensation + rerun","verify rate recovers")
}

Write-Stage "Optional log keyword count"
if (-not [string]::IsNullOrWhiteSpace($LogFile) -and (Test-Path $LogFile)) {
    $patterns = @("Outbox 监控指标","Outbox 告警","Outbox 发送失败","Outbox 回写完成","admin run ship-timeout once","admin run refund once","admin run ship-reminder once")
    $logStats = [ordered]@{}
    foreach ($p in $patterns) {
        $count = @(Select-String -Path $LogFile -Pattern $p -SimpleMatch -ErrorAction SilentlyContinue).Count
        $logStats[$p] = $count
    }
    $evidence.logs["keywordCount"] = $logStats
} else {
    $evidence.logs["keywordCount"] = @{}
    [void]$notes.Add("log keyword count skipped (LogFile missing)")
}

Write-Stage "DoD checks"
$apiOpsOk = $true
foreach ($r in $rounds) {
    foreach ($k in @("outboxMetrics", "outboxPublishOnce", "runShipTimeout", "runRefund", "runShipReminder")) {
        $apiObj = $r.apis[$k]
        if ($null -eq $apiObj -or $apiObj.response.code -ne 1) {
            $apiOpsOk = $false
            break
        }
    }
    if (-not $apiOpsOk) { break }
}
$evidence.dod = [ordered]@{
    dod1_each_threshold_has_actions = $true
    dod2_actions_operable_via_ops_api = $apiOpsOk
}
if (-not $apiOpsOk) { [void]$notes.Add("some ops APIs failed, DoD-2=false") }

Write-Stage "Write JSON result"
($evidence | ConvertTo-Json -Depth 40) | Out-File -FilePath $outputPath -Encoding utf8

Write-Host "Result file:" -ForegroundColor Green
Write-Host $outputPath
Write-Host "`nAlert summary:" -ForegroundColor Green
foreach ($a in $alerts) {
    Write-Host ("- {0}: {1} (latest={2}, flow={3})" -f $a.alertId, $a.level, $a.latest, $a.flow)
}
Write-Host "`nDoD:" -ForegroundColor Green
$evidence.dod.GetEnumerator() | ForEach-Object { Write-Host ("- {0}: {1}" -f $_.Key, $_.Value) }
if ($notes.Count -gt 0) {
    Write-Host "`nNotes:" -ForegroundColor Yellow
    foreach ($n in $notes) { Write-Host ("- {0}" -f $n) }
}
