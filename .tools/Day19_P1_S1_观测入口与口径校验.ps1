param(
    # 本地服务地址（默认你的 8080）。
    [string]$BaseUrl = "http://localhost:8080",

    # 结果 JSON 输出目录。留空则自动写到 day19回归/执行记录。
    [string]$RecordDir = "",

    # 应用日志文件路径（可选）：用于 C2（审计 auditId 可检索）检查。
    [string]$LogFile = "",

    # 普通用户账号：用于触发 USER_LOGIN 的成功/失败两条链路。
    [string]$UserLoginId = "13800000001",
    [string]$UserPasswordOk = "123456",
    [string]$UserPasswordBad = "wrong123",

    # 管理员账号：用于调用 /admin/ops/** 运维接口。
    [string]$AdminLoginId = "13900000001",
    [string]$AdminPasswordOk = "admin123",
    [string]$AdminPasswordBad = "wrong123",

    # Outbox 事件 ID（可选）：用于 C5 单事件补偿验证。
    [string]$OutboxEventId = "",

    # 任务补偿参数（可选）：用于 C8。
    # TaskCompensateType 允许：ship-timeout / refund / ship-reminder
    [string]$TaskCompensateType = "",
    [long]$TaskId = 0
)

<#
脚本用途（Day19 P1-S1）：
1) 自动触发“审计/Outbox/任务”三类观测入口；
2) 统一输出 action/actor/result/errorCode/costMs；
3) 自动计算 C1~C10，产出可回填执行记录的 JSON 证据。

名词解释：
- LogFile：应用日志文件路径，用来做 C2（auditId 可检索）检查。
- OutboxEventId：message_outbox 表中的 event_id，用来做 C5（单事件补偿）检查。
- TaskCompensateType：任务补偿类型，可选 ship-timeout / refund / ship-reminder。
- TaskId：任务主键 ID（不是订单号），配合 TaskCompensateType 做 C8 检查。

常用命令：
1) 基础跑法：
   powershell -ExecutionPolicy Bypass -File .tools/Day19_P1_S1_观测入口与口径校验.ps1
2) 全量跑法（含 C2/C5/C8）：
   powershell -ExecutionPolicy Bypass -File .tools/Day19_P1_S1_观测入口与口径校验.ps1 `
     -LogFile '_tmp_day18_app8080.out.log' `
     -OutboxEventId 'xxxx-event-id' `
     -TaskCompensateType 'ship-timeout' -TaskId 247
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Stage {
    param([string]$Message)
    Write-Host ("`n[Day19-P1-S1] {0}" -f $Message) -ForegroundColor Cyan
}

function Sanitize-Value {
    param([object]$Value)

    # 写入证据前先脱敏，避免 token/password 等敏感信息落盘。
    if ($null -eq $Value) { return $null }
    if ($Value -is [string]) { return $Value }

    if ($Value -is [System.Collections.IDictionary]) {
        $obj = @{}
        foreach ($k in $Value.Keys) {
            $key = [string]$k
            if ($key -match "(?i)token|password|secret|sign|authorization") {
                $obj[$key] = "<redacted>"
            } else {
                $obj[$key] = Sanitize-Value $Value[$k]
            }
        }
        return $obj
    }

    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        $arr = @()
        foreach ($item in $Value) {
            $arr += ,(Sanitize-Value $item)
        }
        return $arr
    }

    if ($Value -is [psobject]) {
        $obj = @{}
        foreach ($p in $Value.PSObject.Properties) {
            if ($p.Name -match "(?i)token|password|secret|sign|authorization") {
                $obj[$p.Name] = "<redacted>"
            } else {
                $obj[$p.Name] = Sanitize-Value $p.Value
            }
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
            try {
                $ret.httpStatus = [int]$_.Exception.Response.StatusCode
            } catch {
                $ret.httpStatus = -1
            }
            try {
                $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $raw = $sr.ReadToEnd()
                $sr.Close()
                if ($raw) {
                    try { $ret.response = $raw | ConvertFrom-Json } catch { $ret.response = $raw }
                }
            } catch {
                # ignore
            }
        }
    } finally {
        $sw.Stop()
        $ret.costMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
    }

    return [pscustomobject]$ret
}

function Get-ApiCode { param([psobject]$r) if($null -eq $r -or $null -eq $r.response){return $null}; if($r.response.PSObject.Properties.Name -contains "code"){return $r.response.code}; return $null }
function Get-ApiMsg { param([psobject]$r) if($null -eq $r -or $null -eq $r.response){return $null}; if($r.response.PSObject.Properties.Name -contains "msg"){return [string]$r.response.msg}; return $null }
function Get-ApiData { param([psobject]$r) if($null -eq $r -or $null -eq $r.response){return $null}; if($r.response.PSObject.Properties.Name -contains "data"){return $r.response.data}; return $null }
function Resolve-ResultByCode { param([object]$Code) if($Code -eq 1){return "SUCCESS"}; return "FAILED" }

function Resolve-TaskCompensationPath {
    param([string]$Type, [long]$Id)
    switch ($Type) {
        "ship-timeout" { return "/admin/ops/tasks/ship-timeout/$Id/trigger-now" }
        "refund" { return "/admin/ops/tasks/refund/$Id/reset" }
        "ship-reminder" { return "/admin/ops/tasks/ship-reminder/$Id/trigger-now" }
        default { return $null }
    }
}

function Has-Keys {
    param([object]$Target, [string[]]$Keys)
    if ($null -eq $Target) { return $false }
    foreach ($k in $Keys) {
        if (-not ($Target.PSObject.Properties.Name -contains $k)) { return $false }
    }
    return $true
}

function Add-UnifiedRow {
    param(
        [System.Collections.ArrayList]$Rows,
        [string]$EntryType,
        [string]$Action,
        [string]$Actor,
        [string]$ResultText,
        [string]$ErrorCode,
        [double]$CostMs,
        [bool]$ServerMeasured,
        [string]$RawRef
    )
    $row = [ordered]@{
        entryType = $EntryType
        action = $Action
        actor = $Actor
        result = $ResultText
        errorCode = $ErrorCode
        costMs = $CostMs
        serverMeasured = $ServerMeasured
        rawRef = $RawRef
    }
    [void]$Rows.Add($row)
}

function Write-Glossary {
    Write-Host "`n术语说明（给实习同学版）:" -ForegroundColor Magenta
    Write-Host "- C2 对应审计日志检索：需要你提供 -LogFile（日志文件路径）。"
    Write-Host "- C5 对应 Outbox 单事件补偿：需要 -OutboxEventId（message_outbox.event_id）。"
    Write-Host "- C8 对应任务补偿：需要 -TaskCompensateType + -TaskId。"
    Write-Host "- OutboxEventId 是消息事件唯一标识，不是订单号。"
    Write-Host "- TaskId 是任务表主键，不是订单号。"
}

Write-Stage "初始化输出目录与证据对象"

# 使用 Unicode 码点拼目录，避免 WinPS5 在非 UTF-8 源码场景出现中文乱码路径。
if ([string]::IsNullOrWhiteSpace($RecordDir)) {
    $sep = [System.IO.Path]::DirectorySeparatorChar
    $day19Name = "day19" + [char]0x56DE + [char]0x5F52
    $recordName = [char]0x6267 + [char]0x884C + [char]0x8BB0 + [char]0x5F55
    $RecordDir = $day19Name + $sep + $recordName
}

if (-not (Test-Path $RecordDir)) { New-Item -ItemType Directory -Path $RecordDir -Force | Out-Null }
Write-Host ("当前 BaseUrl: {0}" -f $BaseUrl) -ForegroundColor DarkCyan

$runAt = Get-Date
$timestamp = $runAt.ToString("yyyy-MM-dd_HH-mm-ss")
$outputPath = Join-Path $RecordDir ("Day19_P1_S1_Dynamic_Result_{0}.json" -f $timestamp)
$rows = New-Object System.Collections.ArrayList
$notes = New-Object System.Collections.ArrayList

$evidence = [ordered]@{
    meta = [ordered]@{
        runAt = $runAt.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        note = "敏感字段已脱敏"
    }
    discovery = [ordered]@{ audit=@{}; outbox=@{}; task=@{} }
    unifiedSchema = [ordered]@{ fields=@("action","actor","result","errorCode","costMs"); rows=$rows }
    checklist = @{}
    logs = @{}
    notes = $notes
}

# 阶段 A（审计入口）：同一动作做“失败+成功”两条请求，验证审计链路是否可观察。
Write-Stage "阶段 A：触发 USER_LOGIN 与 ADMIN_LOGIN"
$userLoginFail = Invoke-ApiWithTiming -Name "user_login_fail" -Method "POST" -Url ($BaseUrl + "/user/auth/login/password") -Body @{ loginId=$UserLoginId; password=$UserPasswordBad }
$userLoginOk = Invoke-ApiWithTiming -Name "user_login_success" -Method "POST" -Url ($BaseUrl + "/user/auth/login/password") -Body @{ loginId=$UserLoginId; password=$UserPasswordOk }
$adminLoginFail = Invoke-ApiWithTiming -Name "admin_login_fail" -Method "POST" -Url ($BaseUrl + "/admin/employee/login") -Body @{ loginId=$AdminLoginId; password=$AdminPasswordBad }
$adminLoginOk = Invoke-ApiWithTiming -Name "admin_login_success" -Method "POST" -Url ($BaseUrl + "/admin/employee/login") -Body @{ loginId=$AdminLoginId; password=$AdminPasswordOk }

$evidence.discovery.audit["user_login_fail"] = Sanitize-Value $userLoginFail
$evidence.discovery.audit["user_login_success"] = Sanitize-Value $userLoginOk
$evidence.discovery.audit["admin_login_fail"] = Sanitize-Value $adminLoginFail
$evidence.discovery.audit["admin_login_success"] = Sanitize-Value $adminLoginOk

$userFailCode = Get-ApiCode $userLoginFail
$userOkCode = Get-ApiCode $userLoginOk
$adminFailCode = Get-ApiCode $adminLoginFail
$adminOkCode = Get-ApiCode $adminLoginOk

Add-UnifiedRow -Rows $rows -EntryType "AUDIT" -Action "USER_LOGIN" -Actor ("USER:{0}" -f $UserLoginId) -ResultText (Resolve-ResultByCode $userFailCode) -ErrorCode (Get-ApiMsg $userLoginFail) -CostMs $userLoginFail.costMs -ServerMeasured $false -RawRef "POST /user/auth/login/password bad"
Add-UnifiedRow -Rows $rows -EntryType "AUDIT" -Action "USER_LOGIN" -Actor ("USER:{0}" -f $UserLoginId) -ResultText (Resolve-ResultByCode $userOkCode) -ErrorCode $null -CostMs $userLoginOk.costMs -ServerMeasured $false -RawRef "POST /user/auth/login/password ok"
Add-UnifiedRow -Rows $rows -EntryType "AUDIT" -Action "ADMIN_LOGIN" -Actor ("ADMIN:{0}" -f $AdminLoginId) -ResultText (Resolve-ResultByCode $adminFailCode) -ErrorCode (Get-ApiMsg $adminLoginFail) -CostMs $adminLoginFail.costMs -ServerMeasured $false -RawRef "POST /admin/employee/login bad"
Add-UnifiedRow -Rows $rows -EntryType "AUDIT" -Action "ADMIN_LOGIN" -Actor ("ADMIN:{0}" -f $AdminLoginId) -ResultText (Resolve-ResultByCode $adminOkCode) -ErrorCode $null -CostMs $adminLoginOk.costMs -ServerMeasured $false -RawRef "POST /admin/employee/login ok"

$adminToken = $null
$adminData = Get-ApiData $adminLoginOk
if ($null -ne $adminData -and ($adminData.PSObject.Properties.Name -contains "token")) { $adminToken = [string]$adminData.token }
$hAdmin = @{}
if (-not [string]::IsNullOrWhiteSpace($adminToken)) { $hAdmin["token"] = $adminToken } else { [void]$notes.Add("未拿到管理员 token，已跳过 Outbox/Task 检查。") }

# 阶段 B（Outbox 入口）：先看指标，再跑 publish-once；如给 eventId，再做单事件补偿。
Write-Stage "阶段 B：Outbox 检查"
$outboxMetricsBefore = $null
$outboxPublishOnce = $null
$outboxMetricsAfter = $null
$outboxEventTrigger = $null
$outboxEventQuery = $null

if ($hAdmin.Count -gt 0) {
    $outboxMetricsBefore = Invoke-ApiWithTiming -Name "outbox_metrics_before" -Method "GET" -Url ($BaseUrl + "/admin/ops/outbox/metrics") -Headers $hAdmin
    $outboxPublishOnce = Invoke-ApiWithTiming -Name "outbox_publish_once" -Method "POST" -Url ($BaseUrl + "/admin/ops/outbox/publish-once?limit=20") -Headers $hAdmin
    $outboxMetricsAfter = Invoke-ApiWithTiming -Name "outbox_metrics_after" -Method "GET" -Url ($BaseUrl + "/admin/ops/outbox/metrics") -Headers $hAdmin
    if (-not [string]::IsNullOrWhiteSpace($OutboxEventId)) {
        $outboxEventTrigger = Invoke-ApiWithTiming -Name "outbox_event_trigger_now" -Method "POST" -Url ($BaseUrl + "/admin/ops/outbox/event/" + $OutboxEventId + "/trigger-now") -Headers $hAdmin
        $outboxEventQuery = Invoke-ApiWithTiming -Name "outbox_event_query" -Method "GET" -Url ($BaseUrl + "/admin/ops/outbox/event/" + $OutboxEventId) -Headers $hAdmin
    }
}

$evidence.discovery.outbox["metrics_before"] = Sanitize-Value $outboxMetricsBefore
$evidence.discovery.outbox["publish_once"] = Sanitize-Value $outboxPublishOnce
$evidence.discovery.outbox["metrics_after"] = Sanitize-Value $outboxMetricsAfter
$evidence.discovery.outbox["event_trigger_now"] = Sanitize-Value $outboxEventTrigger
$evidence.discovery.outbox["event_query"] = Sanitize-Value $outboxEventQuery

if ($null -ne $outboxMetricsBefore) {
    $code = Get-ApiCode $outboxMetricsBefore; $err = $null; if ($code -ne 1) { $err = Get-ApiMsg $outboxMetricsBefore }
    Add-UnifiedRow -Rows $rows -EntryType "OUTBOX" -Action "OUTBOX_METRICS_QUERY" -Actor "ADMIN:-" -ResultText (Resolve-ResultByCode $code) -ErrorCode $err -CostMs $outboxMetricsBefore.costMs -ServerMeasured $false -RawRef "GET /admin/ops/outbox/metrics"
}
if ($null -ne $outboxPublishOnce) {
    $code = Get-ApiCode $outboxPublishOnce; $err = $null; if ($code -ne 1) { $err = Get-ApiMsg $outboxPublishOnce }
    Add-UnifiedRow -Rows $rows -EntryType "OUTBOX" -Action "OUTBOX_PUBLISH_ONCE" -Actor "ADMIN:-" -ResultText (Resolve-ResultByCode $code) -ErrorCode $err -CostMs $outboxPublishOnce.costMs -ServerMeasured $false -RawRef "POST /admin/ops/outbox/publish-once"
}
if ($null -ne $outboxEventTrigger) {
    $code = Get-ApiCode $outboxEventTrigger; $err = $null; if ($code -ne 1) { $err = Get-ApiMsg $outboxEventTrigger }
    Add-UnifiedRow -Rows $rows -EntryType "OUTBOX" -Action "OUTBOX_TRIGGER_NOW" -Actor "ADMIN:-" -ResultText (Resolve-ResultByCode $code) -ErrorCode $err -CostMs $outboxEventTrigger.costMs -ServerMeasured $false -RawRef ("POST /admin/ops/outbox/event/" + $OutboxEventId + "/trigger-now")
}

# 阶段 C（任务入口）：三类任务都执行 list + run-once；可选再做单条补偿。
Write-Stage "阶段 C：任务检查"
$taskShipTimeoutList = $null
$taskRefundList = $null
$taskShipReminderList = $null
$taskShipTimeoutRun = $null
$taskRefundRun = $null
$taskShipReminderRun = $null
$taskCompensate = $null

if ($hAdmin.Count -gt 0) {
    $urlListA = $BaseUrl + "/admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=20"
    $urlListB = $BaseUrl + "/admin/ops/tasks/refund?status=FAILED&page=1&pageSize=20"
    $urlListC = $BaseUrl + "/admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=20"
    $taskShipTimeoutList = Invoke-ApiWithTiming -Name "task_list_ship_timeout" -Method "GET" -Url $urlListA -Headers $hAdmin
    $taskRefundList = Invoke-ApiWithTiming -Name "task_list_refund" -Method "GET" -Url $urlListB -Headers $hAdmin
    $taskShipReminderList = Invoke-ApiWithTiming -Name "task_list_ship_reminder" -Method "GET" -Url $urlListC -Headers $hAdmin

    $taskShipTimeoutRun = Invoke-ApiWithTiming -Name "task_run_ship_timeout" -Method "POST" -Url ($BaseUrl + "/admin/ops/tasks/ship-timeout/run-once?limit=20") -Headers $hAdmin
    $taskRefundRun = Invoke-ApiWithTiming -Name "task_run_refund" -Method "POST" -Url ($BaseUrl + "/admin/ops/tasks/refund/run-once?limit=20") -Headers $hAdmin
    $taskShipReminderRun = Invoke-ApiWithTiming -Name "task_run_ship_reminder" -Method "POST" -Url ($BaseUrl + "/admin/ops/tasks/ship-reminder/run-once?limit=20") -Headers $hAdmin

    if (-not [string]::IsNullOrWhiteSpace($TaskCompensateType) -and $TaskId -gt 0) {
        $compPath = Resolve-TaskCompensationPath -Type $TaskCompensateType -Id $TaskId
        if ($null -ne $compPath) {
            $taskCompensate = Invoke-ApiWithTiming -Name "task_compensate" -Method "POST" -Url ($BaseUrl + $compPath) -Headers $hAdmin
        } else {
            [void]$notes.Add("TaskCompensateType 无效，可选值：ship-timeout/refund/ship-reminder。")
        }
    }
}

$evidence.discovery.task["list_ship_timeout"] = Sanitize-Value $taskShipTimeoutList
$evidence.discovery.task["list_refund"] = Sanitize-Value $taskRefundList
$evidence.discovery.task["list_ship_reminder"] = Sanitize-Value $taskShipReminderList
$evidence.discovery.task["run_ship_timeout"] = Sanitize-Value $taskShipTimeoutRun
$evidence.discovery.task["run_refund"] = Sanitize-Value $taskRefundRun
$evidence.discovery.task["run_ship_reminder"] = Sanitize-Value $taskShipReminderRun
$evidence.discovery.task["compensate"] = Sanitize-Value $taskCompensate

foreach ($api in @($taskShipTimeoutList, $taskRefundList, $taskShipReminderList)) {
    if ($null -ne $api) {
        $code = Get-ApiCode $api; $err = $null; if ($code -ne 1) { $err = Get-ApiMsg $api }
        Add-UnifiedRow -Rows $rows -EntryType "TASK" -Action "TASK_LIST" -Actor "ADMIN:-" -ResultText (Resolve-ResultByCode $code) -ErrorCode $err -CostMs $api.costMs -ServerMeasured $false -RawRef $api.url
    }
}
foreach ($api in @($taskShipTimeoutRun, $taskRefundRun, $taskShipReminderRun)) {
    if ($null -ne $api) {
        $code = Get-ApiCode $api; $err = $null; if ($code -ne 1) { $err = Get-ApiMsg $api }
        Add-UnifiedRow -Rows $rows -EntryType "TASK" -Action "TASK_RUN_ONCE" -Actor "ADMIN:-" -ResultText (Resolve-ResultByCode $code) -ErrorCode $err -CostMs $api.costMs -ServerMeasured $false -RawRef $api.url
    }
}
if ($null -ne $taskCompensate) {
    $code = Get-ApiCode $taskCompensate; $err = $null; if ($code -ne 1) { $err = Get-ApiMsg $taskCompensate }
    Add-UnifiedRow -Rows $rows -EntryType "TASK" -Action "TASK_COMPENSATE" -Actor "ADMIN:-" -ResultText (Resolve-ResultByCode $code) -ErrorCode $err -CostMs $taskCompensate.costMs -ServerMeasured $false -RawRef $taskCompensate.url
}

# 阶段 D（日志采样）：若传入 -LogFile，则抽取 AUDIT 行并用于 C2 验证。
Write-Stage "阶段 D：可选的审计日志采样"
$auditLogLines = @()
if (-not [string]::IsNullOrWhiteSpace($LogFile) -and (Test-Path $LogFile)) {
    $auditLogLines = @(
        Select-String -Path $LogFile -Pattern "AUDIT auditId=" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Line |
            Select-Object -Last 50
    )
    $evidence.logs["audit_last50"] = $auditLogLines
} else {
    $evidence.logs["audit_last50"] = @()
    [void]$notes.Add("未找到日志文件，C2 无法自动通过（请传 -LogFile）。")
}

# 阶段 E（自动验收）：把接口和日志结果转成 C1~C10 勾选结果。
Write-Stage "阶段 E：计算检查项 C1..C10"
$metricsData = Get-ApiData $outboxMetricsBefore
$publishData = Get-ApiData $outboxPublishOnce
$compData = Get-ApiData $taskCompensate

$c1 = ($userOkCode -eq 1 -and $userFailCode -ne 1 -and $userFailCode -ne $null)
$c2 = ($auditLogLines.Count -gt 0)
$c3 = Has-Keys -Target $metricsData -Keys @("new","sent","fail","failRetrySum")
$c4 = Has-Keys -Target $publishData -Keys @("pulled","sent","failed")
$c5 = $false; if ($null -ne $outboxEventTrigger) { $c5 = ((Get-ApiCode $outboxEventTrigger) -eq 1) }
$c6 = ((Get-ApiCode $taskShipTimeoutList) -eq 1 -and (Get-ApiCode $taskRefundList) -eq 1 -and (Get-ApiCode $taskShipReminderList) -eq 1)
$c7 = ((Get-ApiCode $taskShipTimeoutRun) -eq 1 -and (Get-ApiCode $taskRefundRun) -eq 1 -and (Get-ApiCode $taskShipReminderRun) -eq 1)
$c8 = $false; if ($null -ne $taskCompensate) { $c8 = ((Get-ApiCode $taskCompensate) -eq 1 -and $null -ne $compData -and ($compData.PSObject.Properties.Name -contains "updatedRows")) }
$c9 = $true
foreach ($r in $rows) {
    if ([string]::IsNullOrWhiteSpace([string]$r.action) -or [string]::IsNullOrWhiteSpace([string]$r.actor) -or [string]::IsNullOrWhiteSpace([string]$r.result)) { $c9 = $false; break }
}
$entryTypes = @($rows | ForEach-Object { $_.entryType } | Sort-Object -Unique)
$c10 = ($entryTypes -contains "AUDIT" -and $entryTypes -contains "OUTBOX" -and $entryTypes -contains "TASK")

$evidence.checklist = [ordered]@{
    c1_audit_success_and_failed = $c1
    c2_auditid_searchable = $c2
    c3_outbox_metrics_fields = $c3
    c4_outbox_publish_fields = $c4
    c5_outbox_single_event_recovery = $c5
    c6_task_list_available = $c6
    c7_task_run_once_available = $c7
    c8_task_compensation_available = $c8
    c9_unified_schema_mapped = $c9
    c10_each_entry_has_repro = $c10
}
if (-not $c5) { [void]$notes.Add("C5 未通过：请重跑并传 -OutboxEventId。") }
if (-not $c8) { [void]$notes.Add("C8 未通过：请重跑并传 -TaskCompensateType 与 -TaskId。") }

# 阶段 F（证据落盘）：保存 JSON 结果，供执行记录文档回填引用。
Write-Stage "阶段 F：写入 JSON 结果"
$json = $evidence | ConvertTo-Json -Depth 30
$json | Out-File -FilePath $outputPath -Encoding utf8

Write-Host "结果文件:" -ForegroundColor Green
Write-Host $outputPath
Write-Host "`n检查清单（True=通过，False=未执行或未通过）:" -ForegroundColor Green
$evidence.checklist.GetEnumerator() | ForEach-Object { Write-Host ("- {0}: {1}" -f $_.Key, $_.Value) }
if ($notes.Count -gt 0) {
    Write-Host "`n备注:" -ForegroundColor Yellow
    foreach ($n in $notes) { Write-Host ("- {0}" -f $n) }
}
Write-Glossary
