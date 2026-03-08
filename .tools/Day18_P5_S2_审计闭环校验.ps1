param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$LogFile = "_tmp_day18_app8080.out.log",
    [string]$TargetLoginId = "13800000001",
    [string]$TargetPassword = "123456",
    [string]$WrongPassword = "wrong123",
    [string]$AdminLoginId = "13900000001",
    [string]$AdminPassword = "admin123",
    [long]$FallbackTargetUserId = 1
)

<#
脚本用途（Day18 P5-S2 审计闭环校验）：
1) 自动触发“错误登录 -> 风控冻结 -> 管理员解封 -> 正常登录”完整场景；
2) 从日志文件抽取 USER_LOGIN / LOGIN_RISK_FREEZE / USER_UNBAN 三类 AUDIT 样本；
3) 输出结构化证据 JSON，并给出 closed_loop_pass 判定。

关键参数：
- BaseUrl：服务地址（默认 8080）。
- LogFile：日志文件路径（必填可用路径，否则无法抽样审计日志）。
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Sanitize-Value {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [string]) {
        return $Value
    }

    if ($Value -is [System.Collections.IDictionary]) {
        $obj = [ordered]@{}
        foreach ($k in $Value.Keys) {
            $key = [string]$k
            if ($key -match "(?i)token|password|secret|sign") {
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
            $arr += @(Sanitize-Value $item)
        }
        return $arr
    }

    if ($Value -is [psobject]) {
        $obj = [ordered]@{}
        foreach ($p in $Value.PSObject.Properties) {
            if ($p.Name -match "(?i)token|password|secret|sign") {
                $obj[$p.Name] = "<redacted>"
            } else {
                $obj[$p.Name] = Sanitize-Value $p.Value
            }
        }
        return $obj
    }

    return $Value
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )

    $ret = [ordered]@{
        method = $Method
        url = $Url
        at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
        httpStatus = $null
        response = $null
        error = $null
    }

    try {
        if ($null -ne $Body) {
            $jsonBody = $Body | ConvertTo-Json -Depth 20 -Compress
            $resp = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 15
        } else {
            $resp = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -TimeoutSec 15
        }
        $ret.httpStatus = 200
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
                    try {
                        $ret.response = $raw | ConvertFrom-Json
                    } catch {
                        $ret.response = $raw
                    }
                }
            } catch {
                # ignore parse failures
            }
        }
    }

    return [pscustomobject]$ret
}

function Api-Summary {
    param([pscustomobject]$ApiResult)

    $summary = [ordered]@{
        httpStatus = $ApiResult.httpStatus
        code = $null
        msg = $null
        data = $null
        error = $ApiResult.error
    }

    if ($ApiResult.response) {
        if ($ApiResult.response.PSObject.Properties.Name -contains "code") {
            $summary.code = $ApiResult.response.code
        }
        if ($ApiResult.response.PSObject.Properties.Name -contains "msg") {
            $summary.msg = $ApiResult.response.msg
        }
        if ($ApiResult.response.PSObject.Properties.Name -contains "data") {
            $summary.data = Sanitize-Value $ApiResult.response.data
        } else {
            $summary.data = Sanitize-Value $ApiResult.response
        }
    }

    return $summary
}

# 兼容历史日志命名：优先使用传入路径；不存在时自动选择最新的 _tmp_day18_app*.out.log。
if (-not (Test-Path $LogFile)) {
    $candidate = Get-ChildItem -File -Filter "_tmp_day18_app*.out.log" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($candidate) {
        $LogFile = $candidate.FullName
        Write-Host "[Day18-P5-S2] 未找到指定日志，已自动改用: $LogFile" -ForegroundColor Yellow
    }
}
if (-not (Test-Path $LogFile)) {
    throw "log file not found: $LogFile"
}

$recordMarker = Get-ChildItem -Path (Get-Location) -Recurse -Filter "Day18_P7_S1_newman_result.json" | Select-Object -First 1
if (-not $recordMarker) {
    throw "cannot locate Day18 execution record directory"
}
$recordDir = Split-Path -Parent $recordMarker.FullName

$runAt = Get-Date
$timestamp = $runAt.ToString("yyyy-MM-dd_HH-mm-ss")
$outputPath = Join-Path $recordDir ("Day18_P5_S2_Audit_CloseLoop_Result_{0}.json" -f $timestamp)

$startedAt = Get-Date

$adminLogin = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/employee/login" -Body @{
    loginId = $AdminLoginId
    password = $AdminPassword
}
if (-not $adminLogin.response -or $adminLogin.response.code -ne 1 -or -not $adminLogin.response.data.token) {
    throw "admin login failed on $BaseUrl"
}
$adminToken = [string]$adminLogin.response.data.token
$hAdmin = @{ token = $adminToken }

$targetProbe = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{
    loginId = $TargetLoginId
    password = $TargetPassword
}

$targetUserId = $FallbackTargetUserId
if ($targetProbe.response -and $targetProbe.response.code -eq 1 -and $targetProbe.response.data -and $targetProbe.response.data.user -and $targetProbe.response.data.user.id) {
    $targetUserId = [long]$targetProbe.response.data.user.id
}

# Ensure active before freeze scenario to avoid old state contamination.
$preUnban1 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$targetUserId/unban" -Headers $hAdmin
$preUnban2 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$targetUserId/unban" -Headers $hAdmin
$ensureActiveLogin = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{
    loginId = $TargetLoginId
    password = $TargetPassword
}

$wrongAttempts = @()
1..5 | ForEach-Object {
    $wrongAttempts += @(Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{
                loginId = $TargetLoginId
                password = $WrongPassword
            })
}

$attempt6Wrong = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{
    loginId = $TargetLoginId
    password = $WrongPassword
}
$attemptAfterFreezeCorrect = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{
    loginId = $TargetLoginId
    password = $TargetPassword
}

$unban1 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$targetUserId/unban" -Headers $hAdmin
$unban2 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$targetUserId/unban" -Headers $hAdmin
$finalLogin = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{
    loginId = $TargetLoginId
    password = $TargetPassword
}

Start-Sleep -Milliseconds 500

$allAuditLines = @(
    Select-String -Path $LogFile -Pattern "action=(USER_LOGIN|LOGIN_RISK_FREEZE|USER_UNBAN)" -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Line
)
$auditLines = @(
    $allAuditLines | Where-Object {
        $line = $_
        if ($line.Length -ge 23) {
            try {
                $ts = [datetime]::ParseExact($line.Substring(0, 23), "yyyy-MM-dd HH:mm:ss.fff", $null)
                return $ts -ge $startedAt
            } catch {
                return $true
            }
        }
        return $true
    }
)

$auditSamples = [ordered]@{
    user_login = @($auditLines | Where-Object { $_ -match "action=USER_LOGIN" } | Select-Object -First 8)
    login_risk_freeze = @($auditLines | Where-Object { $_ -match "action=LOGIN_RISK_FREEZE" } | Select-Object -First 4)
    user_unban = @($auditLines | Where-Object { $_ -match "action=USER_UNBAN" } | Select-Object -First 4)
}

$fieldCheck = [ordered]@{
    user_login_has_required_fields = $false
    login_risk_freeze_has_required_fields = $false
    user_unban_has_required_fields = $false
}

if ($auditSamples.user_login.Count -gt 0) {
    $line = $auditSamples.user_login[0]
    $fieldCheck.user_login_has_required_fields = ($line -match "auditId=" -and $line -match "action=USER_LOGIN" -and $line -match "actorType=" -and $line -match "actorId=" -and $line -match "targetType=" -and $line -match "targetId=" -and $line -match "result=" -and $line -match "ip=")
}
if ($auditSamples.login_risk_freeze.Count -gt 0) {
    $line = $auditSamples.login_risk_freeze[0]
    $fieldCheck.login_risk_freeze_has_required_fields = ($line -match "auditId=" -and $line -match "action=LOGIN_RISK_FREEZE" -and $line -match "actorType=" -and $line -match "actorId=" -and $line -match "targetType=" -and $line -match "targetId=" -and $line -match "result=" -and $line -match "ip=" -and $line -match "detail=")
}
if ($auditSamples.user_unban.Count -gt 0) {
    $line = $auditSamples.user_unban[0]
    $fieldCheck.user_unban_has_required_fields = ($line -match "auditId=" -and $line -match "action=USER_UNBAN" -and $line -match "actorType=" -and $line -match "actorId=" -and $line -match "targetType=" -and $line -match "targetId=" -and $line -match "result=" -and $line -match "ip=" -and $line -match "detail=")
}

$result = [ordered]@{
    meta = [ordered]@{
        runAt = $runAt.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        logFile = $LogFile
        targetLoginId = $TargetLoginId
        targetUserId = $targetUserId
        note = "credentials/tokens are redacted in this artifact"
    }
    scenario = [ordered]@{
        probe_login = Api-Summary $targetProbe
        pre_unban_first = Api-Summary $preUnban1
        pre_unban_repeat = Api-Summary $preUnban2
        ensure_active_login = Api-Summary $ensureActiveLogin
        wrong_attempts_1_to_5 = @($wrongAttempts | ForEach-Object { Api-Summary $_ })
        wrong_attempt_6 = Api-Summary $attempt6Wrong
        correct_attempt_while_frozen = Api-Summary $attemptAfterFreezeCorrect
        unban_first = Api-Summary $unban1
        unban_repeat = Api-Summary $unban2
        final_login_after_unban = Api-Summary $finalLogin
    }
    audit_extract = [ordered]@{
        startedAt = $startedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
        extractedAuditLineCount = $auditLines.Count
        samples = $auditSamples
        fieldCheck = $fieldCheck
    }
    verdict = [ordered]@{
        has_user_login = ($auditSamples.user_login.Count -gt 0)
        has_login_risk_freeze = ($auditSamples.login_risk_freeze.Count -gt 0)
        has_user_unban = ($auditSamples.user_unban.Count -gt 0)
        all_required_fields_present = ($fieldCheck.user_login_has_required_fields -and $fieldCheck.login_risk_freeze_has_required_fields -and $fieldCheck.user_unban_has_required_fields)
        closed_loop_pass = (($auditSamples.user_login.Count -gt 0) -and ($auditSamples.login_risk_freeze.Count -gt 0) -and ($auditSamples.user_unban.Count -gt 0) -and ($fieldCheck.user_login_has_required_fields -and $fieldCheck.login_risk_freeze_has_required_fields -and $fieldCheck.user_unban_has_required_fields))
    }
}

$result | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 $outputPath
Write-Output $outputPath
