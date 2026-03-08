param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$LogFile = "_tmp_day18_app8080.out.log"
)

<#
脚本用途（Day18 闭环回归）：
1) 串行跑 Day18 关键业务链路（登录、下单、支付、回调、风控、任务、Outbox 等）；
2) 把接口返回、数据库核验、日志样本聚合成一份 JSON 证据；
3) 生成 DoD 勾选结果，作为 Day18 收口材料。

关键参数：
- BaseUrl：服务地址（默认 8080）。
- LogFile：应用日志文件路径，用于抽取 AUDIT 和幂等日志样本。
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 兼容历史日志命名：优先使用传入路径；不存在时自动选择最新的 _tmp_day18_app*.out.log。
if (-not (Test-Path $LogFile)) {
    $candidate = Get-ChildItem -File -Filter "_tmp_day18_app*.out.log" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($candidate) {
        $LogFile = $candidate.FullName
        Write-Host "[Day18-CloseLoop] 未找到指定日志，已自动改用: $LogFile" -ForegroundColor Yellow
    }
}

$mysqlExe = "D:\mysql\mysql-8.4.6-winx64\bin\mysql.exe"
if (-not (Test-Path $mysqlExe)) {
    throw "mysql executable not found: $mysqlExe"
}

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
            $resp = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody
        } else {
            $resp = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers
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

function Invoke-MySqlQuery {
    param([string]$Sql)

    $oldMysqlPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = "1234"
    try {
        $raw = & $mysqlExe -uroot -N -B -D secondhand2 -e $Sql 2>&1
    } finally {
        if ($null -eq $oldMysqlPwd) {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $oldMysqlPwd
        }
    }
    if ($LASTEXITCODE -ne 0) {
        throw "mysql failed: $($raw -join "`n")"
    }
    return @($raw)
}

function Get-MySqlScalar {
    param([string]$Sql)
    $rows = @(Invoke-MySqlQuery $Sql)
    if ($rows.Count -eq 0) {
        return ""
    }
    return ([string]$rows[0]).Trim()
}

function New-Product {
    param(
        [string]$Title,
        [string]$Tag,
        [hashtable]$SellerHeaders
    )
    $body = @{
        title = $Title
        description = "Day18 close loop $Tag"
        price = 99.00
        images = @("https://img.example.com/day18/$Tag.jpg")
        category = "Day18"
    }
    return Invoke-Api -Method "POST" -Url "$BaseUrl/user/products" -Headers $SellerHeaders -Body $body
}

function Approve-Product {
    param(
        [long]$ProductId,
        [hashtable]$AdminHeaders
    )
    return Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/products/$ProductId/approve" -Headers $AdminHeaders
}

function New-Order {
    param(
        [long]$ProductId,
        [string]$Address,
        [hashtable]$BuyerHeaders
    )
    $body = @{
        productId = $ProductId
        shippingAddress = $Address
        quantity = 1
    }
    return Invoke-Api -Method "POST" -Url "$BaseUrl/user/orders" -Headers $BuyerHeaders -Body $body
}

function Pay-Order {
    param(
        [long]$OrderId,
        [hashtable]$BuyerHeaders
    )
    return Invoke-Api -Method "POST" -Url "$BaseUrl/user/orders/$OrderId/pay" -Headers $BuyerHeaders
}

function Cancel-Order {
    param(
        [long]$OrderId,
        [hashtable]$BuyerHeaders
    )
    return Invoke-Api -Method "POST" -Url "$BaseUrl/user/orders/$OrderId/cancel" -Headers $BuyerHeaders -Body @{ reason = "day18_close_check" }
}

function Payment-Callback {
    param(
        [string]$OrderNo,
        [decimal]$Amount,
        [string]$TradeNo,
        [string]$Status,
        [long]$Timestamp,
        [string]$Sign = "ok-sign"
    )
    $body = @{
        channel = "mock"
        orderNo = $OrderNo
        tradeNo = $TradeNo
        amount = $Amount
        status = $Status
        timestamp = $Timestamp
        sign = $Sign
    }
    return Invoke-Api -Method "POST" -Url "$BaseUrl/payment/callback" -Body $body
}

function Set-CreditTarget {
    param(
        [long]$UserId,
        [int]$TargetScore,
        [string]$Reason,
        [hashtable]$AdminHeaders
    )
    $beforeResp = Invoke-Api -Method "GET" -Url "$BaseUrl/admin/credit?userId=$UserId" -Headers $AdminHeaders
    $beforeScore = [int]$beforeResp.response.data.creditScore
    $delta = $TargetScore - $beforeScore
    $adjustResp = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/credit/adjust" -Headers $AdminHeaders -Body @{
        userId = $UserId
        delta = $delta
        reason = $Reason
        refId = $null
    }
    $afterResp = Invoke-Api -Method "GET" -Url "$BaseUrl/admin/credit?userId=$UserId" -Headers $AdminHeaders
    return [ordered]@{
        target = $TargetScore
        beforeScore = $beforeScore
        delta = $delta
        adjust = Api-Summary $adjustResp
        after = Api-Summary $afterResp
    }
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$P
    )
    if (-not $Values -or $Values.Count -eq 0) {
        return 0
    }
    $sorted = $Values | Sort-Object
    $idx = [int][Math]::Ceiling($P * $sorted.Count) - 1
    if ($idx -lt 0) { $idx = 0 }
    if ($idx -ge $sorted.Count) { $idx = $sorted.Count - 1 }
    return [Math]::Round([double]$sorted[$idx], 2)
}

$runAt = Get-Date
$runTag = "D18CLOSE" + $runAt.ToString("MMddHHmmss")
$timestamp = $runAt.ToString("yyyy-MM-dd_HH-mm-ss")
$recordMarker = Get-ChildItem -Path (Get-Location) -Recurse -Filter "Day18_P7_S1_newman_result.json" | Select-Object -First 1
if (-not $recordMarker) {
    throw "cannot locate Day18 execution record directory"
}
$recordDir = Split-Path -Parent $recordMarker.FullName
$outputPath = Join-Path $recordDir ("Day18_CloseLoop_Dynamic_Result_{0}.json" -f $timestamp)

$evidence = [ordered]@{
    meta = [ordered]@{
        runAt = $runAt.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        runTag = $runTag
        note = "all credentials/tokens are redacted in this artifact"
    }
    runtime_impact = [ordered]@{}
    p1_s2 = [ordered]@{}
    p4_s1 = [ordered]@{}
    p5_s1 = [ordered]@{}
    p6_s2 = [ordered]@{}
    p7_s2 = [ordered]@{}
    log_samples = [ordered]@{
        audit_actions = @()
        idempotent_hits = @()
    }
    dod = [ordered]@{}
}

# Runtime impact check (from log file)
if (Test-Path $LogFile) {
    $impactLines = @(
        Select-String -Path $LogFile -Pattern "澶勭悊鍙戣揣瓒呮椂浠诲姟澶辫触锛歵askId=27, orderId=900056|鍙戣揣瓒呮椂鍏冲崟鎴愬姛锛歰rderId=900056, taskId=27" -SimpleMatch -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Line
    )
    $evidence.runtime_impact["order_900056_task_27"] = $impactLines
}

# P4 login scenes
$userLoginFail = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{ loginId = "13800000001"; password = "wrong123" }
$userLoginOk = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{ loginId = "13800000001"; password = "123456" }
$sellerLoginOk = Invoke-Api -Method "POST" -Url "$BaseUrl/user/auth/login/password" -Body @{ loginId = "13800000002"; password = "seller123" }
$adminLoginFail = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/employee/login" -Body @{ loginId = "13900000001"; password = "wrong123" }
$adminLoginOk = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/employee/login" -Body @{ loginId = "13900000001"; password = "admin123" }

$evidence.p4_s1["user_login_fail"] = Api-Summary $userLoginFail
$evidence.p4_s1["user_login_success"] = Api-Summary $userLoginOk
$evidence.p4_s1["admin_login_fail"] = Api-Summary $adminLoginFail
$evidence.p4_s1["admin_login_success"] = Api-Summary $adminLoginOk

if ($userLoginOk.response.code -ne 1 -or $sellerLoginOk.response.code -ne 1 -or $adminLoginOk.response.code -ne 1) {
    throw "auth bootstrap failed on $BaseUrl"
}

$buyerToken = [string]$userLoginOk.response.data.token
$buyerId = [long]$userLoginOk.response.data.user.id
$sellerToken = [string]$sellerLoginOk.response.data.token
$sellerId = [long]$sellerLoginOk.response.data.user.id
$adminToken = [string]$adminLoginOk.response.data.token

$hBuyer = @{ authentication = $buyerToken }
$hSeller = @{ authentication = $sellerToken }
$hAdmin = @{ token = $adminToken }

# Bootstrap seller credit to LV3+ before P1/P4 product-order scenes.
$bootstrapSeller = Set-CreditTarget -UserId $sellerId -TargetScore 90 -Reason "day18-close-bootstrap" -AdminHeaders $hAdmin
$evidence.meta["bootstrap_seller_credit"] = $bootstrapSeller

# P1-S2 + P4-S1 C/D
$productA = New-Product -Title ("DAY18-P1-A-{0}" -f $runTag) -Tag $runTag -SellerHeaders $hSeller
$productAId = [long]$productA.response.data.productId
$approveA = Approve-Product -ProductId $productAId -AdminHeaders $hAdmin
$orderA = New-Order -ProductId $productAId -Address ("Day18AddrA-{0}" -f $runTag) -BuyerHeaders $hBuyer
$orderAId = [long]$orderA.response.data.orderId

$payA1 = Pay-Order -OrderId $orderAId -BuyerHeaders $hBuyer
$payA2 = Pay-Order -OrderId $orderAId -BuyerHeaders $hBuyer
$cancelPaid = Cancel-Order -OrderId $orderAId -BuyerHeaders $hBuyer

$runShipTimeout1 = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/ops/tasks/ship-timeout/run-once?limit=100" -Headers $hAdmin
$runShipTimeout2 = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/ops/tasks/ship-timeout/run-once?limit=100" -Headers $hAdmin
$runRefund1 = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/ops/tasks/refund/run-once?limit=100" -Headers $hAdmin
$runRefund2 = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/ops/tasks/refund/run-once?limit=100" -Headers $hAdmin

$productB = New-Product -Title ("DAY18-P1-B-{0}" -f $runTag) -Tag $runTag -SellerHeaders $hSeller
$productBId = [long]$productB.response.data.productId
$approveB = Approve-Product -ProductId $productBId -AdminHeaders $hAdmin
$orderB = New-Order -ProductId $productBId -Address ("Day18AddrB-{0}" -f $runTag) -BuyerHeaders $hBuyer
$orderBId = [long]$orderB.response.data.orderId
$orderBNo = [string]$orderB.response.data.orderNo
$orderBAmount = [decimal]$orderB.response.data.totalAmount

$tsNow = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$cbFail = Payment-Callback -OrderNo $orderBNo -Amount $orderBAmount -TradeNo ("TR-{0}-FAIL" -f $runTag) -Status "FAIL" -Timestamp $tsNow
$cbExpired = Payment-Callback -OrderNo $orderBNo -Amount $orderBAmount -TradeNo ("TR-{0}-EXPIRED" -f $runTag) -Status "SUCCESS" -Timestamp ($tsNow - 7200)
$cbSuccess1 = Payment-Callback -OrderNo $orderBNo -Amount $orderBAmount -TradeNo ("TR-{0}-OK" -f $runTag) -Status "SUCCESS" -Timestamp $tsNow
$cbSuccess2 = Payment-Callback -OrderNo $orderBNo -Amount $orderBAmount -TradeNo ("TR-{0}-OK" -f $runTag) -Status "SUCCESS" -Timestamp $tsNow

$ban1 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$buyerId/ban" -Headers $hAdmin
$ban2 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$buyerId/ban" -Headers $hAdmin
$unban1 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$buyerId/unban" -Headers $hAdmin
$unban2 = Invoke-Api -Method "PUT" -Url "$BaseUrl/admin/user/$buyerId/unban" -Headers $hAdmin

$evidence.p1_s2["product_order_pay_flow"] = [ordered]@{
    createProduct = Api-Summary $productA
    approveProduct = Api-Summary $approveA
    createOrder = Api-Summary $orderA
    payFirst = Api-Summary $payA1
    payRepeat = Api-Summary $payA2
    runShipTimeout1 = Api-Summary $runShipTimeout1
    runShipTimeout2 = Api-Summary $runShipTimeout2
    runRefund1 = Api-Summary $runRefund1
    runRefund2 = Api-Summary $runRefund2
}

$evidence.p4_s1["order_pay_cancel"] = [ordered]@{
    paySuccess = Api-Summary $payA1
    payIdempotent = Api-Summary $payA2
    cancelAfterPay = Api-Summary $cancelPaid
}

$evidence.p4_s1["payment_callback"] = [ordered]@{
    failStatusIgnored = Api-Summary $cbFail
    expiredTimestamp = Api-Summary $cbExpired
    successFirst = Api-Summary $cbSuccess1
    successRepeat = Api-Summary $cbSuccess2
}

$evidence.p4_s1["ban_unban"] = [ordered]@{
    banFirst = Api-Summary $ban1
    banRepeat = Api-Summary $ban2
    unbanFirst = Api-Summary $unban1
    unbanRepeat = Api-Summary $unban2
}

# P1 duplicate SQL checks
$dupChecks = [ordered]@{
    ship_timeout = [int](Get-MySqlScalar "SELECT COUNT(*) FROM (SELECT order_id FROM order_ship_timeout_task GROUP BY order_id HAVING COUNT(*)>1) t;")
    ship_reminder = [int](Get-MySqlScalar "SELECT COUNT(*) FROM (SELECT order_id,level FROM order_ship_reminder_task GROUP BY order_id,level HAVING COUNT(*)>1) t;")
    refund_task = [int](Get-MySqlScalar "SELECT COUNT(*) FROM (SELECT order_id,refund_type FROM order_refund_task GROUP BY order_id,refund_type HAVING COUNT(*)>1) t;")
    refund_idempotency_key = [int](Get-MySqlScalar "SELECT COUNT(*) FROM (SELECT idempotency_key FROM order_refund_task WHERE idempotency_key IS NOT NULL GROUP BY idempotency_key HAVING COUNT(*)>1) t;")
    mq_consume_log = [int](Get-MySqlScalar "SELECT COUNT(*) FROM (SELECT consumer,event_id FROM mq_consume_log GROUP BY consumer,event_id HAVING COUNT(*)>1) t;")
    points_ledger = [int](Get-MySqlScalar "SELECT COUNT(*) FROM (SELECT user_id,biz_type,biz_id FROM points_ledger GROUP BY user_id,biz_type,biz_id HAVING COUNT(*)>1) t;")
}
$evidence.p1_s2["duplicate_sql_checks"] = $dupChecks

# P5-S1 source fields
$sellerCreditBefore = Invoke-Api -Method "GET" -Url "$BaseUrl/admin/credit?userId=$sellerId" -Headers $hAdmin
$sellerStats = [ordered]@{
    completed_as_buyer = [int](Get-MySqlScalar "SELECT COUNT(*) FROM orders WHERE buyer_id=$sellerId AND status='completed';")
    completed_as_seller = [int](Get-MySqlScalar "SELECT COUNT(*) FROM orders WHERE seller_id=$sellerId AND status='completed';")
    cancelled_as_buyer = [int](Get-MySqlScalar "SELECT COUNT(*) FROM orders WHERE buyer_id=$sellerId AND status='cancelled';")
    violation_credit_sum = [int](Get-MySqlScalar "SELECT COALESCE(SUM(credit),0) FROM user_violations WHERE user_id=$sellerId;")
    active_bans = [int](Get-MySqlScalar "SELECT COUNT(*) FROM user_bans WHERE user_id=$sellerId AND start_time <= NOW() AND (end_time IS NULL OR end_time > NOW());")
    admin_adjust_sum = [int](Get-MySqlScalar "SELECT COALESCE(SUM(delta),0) FROM user_credit_logs WHERE user_id=$sellerId AND reason_type='admin_adjust';")
}
$recalcSeller = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/credit/recalc?userId=$sellerId" -Headers $hAdmin
$sellerCreditAfterRecalc = Invoke-Api -Method "GET" -Url "$BaseUrl/admin/credit?userId=$sellerId" -Headers $hAdmin

$lv1Set = Set-CreditTarget -UserId $sellerId -TargetScore 20 -Reason "day18-close-lv1" -AdminHeaders $hAdmin
$lv1Create = New-Product -Title ("DAY18-LV1-{0}" -f $runTag) -Tag $runTag -SellerHeaders $hSeller

Invoke-MySqlQuery "UPDATE products SET status='off_shelf' WHERE owner_id=$sellerId AND is_deleted=0 AND status IN ('under_review','on_sale');" | Out-Null
$activeBeforeLv2 = [int](Get-MySqlScalar "SELECT COUNT(*) FROM products WHERE owner_id=$sellerId AND is_deleted=0 AND status IN ('under_review','on_sale');")

$lv2Set = Set-CreditTarget -UserId $sellerId -TargetScore 65 -Reason "day18-close-lv2" -AdminHeaders $hAdmin
$lv2Attempts = @()
for ($i = 1; $i -le 4; $i++) {
    $attempt = New-Product -Title ("DAY18-LV2-{0}-{1}" -f $runTag, $i) -Tag $runTag -SellerHeaders $hSeller
    $lv2Attempts += @([ordered]@{
        attempt = $i
        result = Api-Summary $attempt
    })
}
$activeAfterLv2 = [int](Get-MySqlScalar "SELECT COUNT(*) FROM products WHERE owner_id=$sellerId AND is_deleted=0 AND status IN ('under_review','on_sale');")

$lv3Set = Set-CreditTarget -UserId $sellerId -TargetScore 90 -Reason "day18-close-lv3" -AdminHeaders $hAdmin
$lv3Create = New-Product -Title ("DAY18-LV3-{0}" -f $runTag) -Tag $runTag -SellerHeaders $hSeller

$evidence.p5_s1 = [ordered]@{
    sellerId = $sellerId
    credit_before = Api-Summary $sellerCreditBefore
    source_stats = $sellerStats
    recalc = Api-Summary $recalcSeller
    credit_after_recalc = Api-Summary $sellerCreditAfterRecalc
    lv1 = [ordered]@{
        set_target = $lv1Set
        create_product = Api-Summary $lv1Create
    }
    lv2 = [ordered]@{
        active_before = $activeBeforeLv2
        set_target = $lv2Set
        attempts = $lv2Attempts
        active_after = $activeAfterLv2
    }
    lv3_plus = [ordered]@{
        set_target = $lv3Set
        create_product = Api-Summary $lv3Create
    }
}

# P6-S2 lightweight load evidence
$jobs = @()
$loadOrderId = $orderAId
$loadUrl = "$BaseUrl/user/orders/$loadOrderId/pay"
$jobCount = 10
$reqPerJob = 10
$loadStart = Get-Date
for ($j = 0; $j -lt $jobCount; $j++) {
    $jobs += Start-Job -ScriptBlock {
        param($Url, $Token, $Count)
        $codeMap = @{}
        $errors = 0
        $lat = New-Object System.Collections.Generic.List[double]
        for ($i = 0; $i -lt $Count; $i++) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $resp = Invoke-RestMethod -Uri $Url -Method Post -Headers @{ authentication = $Token }
                $code = [string]$resp.code
                if (-not $codeMap.ContainsKey($code)) {
                    $codeMap[$code] = 0
                }
                $codeMap[$code] = [int]$codeMap[$code] + 1
            } catch {
                $errors++
            } finally {
                $sw.Stop()
                [void]$lat.Add($sw.Elapsed.TotalMilliseconds)
            }
        }
        [pscustomobject]@{
            codes = $codeMap
            errors = $errors
            count = $Count
            latencies = $lat.ToArray()
        }
    } -ArgumentList $loadUrl, $buyerToken, $reqPerJob
}
Wait-Job -Job $jobs | Out-Null
$jobResults = Receive-Job -Job $jobs
Remove-Job -Job $jobs | Out-Null
$loadElapsed = (Get-Date) - $loadStart

$allLatencies = @()
$codeAgg = @{}
$totalReq = 0
$totalErr = 0
foreach ($r in $jobResults) {
    $totalReq += [int]$r.count
    $totalErr += [int]$r.errors
    $allLatencies += @($r.latencies)
    foreach ($k in $r.codes.Keys) {
        if (-not $codeAgg.ContainsKey($k)) {
            $codeAgg[$k] = 0
        }
        $codeAgg[$k] = [int]$codeAgg[$k] + [int]$r.codes[$k]
    }
}

$evidence.p6_s2 = [ordered]@{
    scenario = "paid-order repeated pay endpoint pressure"
    orderId = $loadOrderId
    totalRequests = $totalReq
    totalErrors = $totalErr
    elapsedSeconds = [Math]::Round($loadElapsed.TotalSeconds, 2)
    throughputRps = if ($loadElapsed.TotalSeconds -gt 0) { [Math]::Round($totalReq / $loadElapsed.TotalSeconds, 2) } else { 0 }
    codeDistribution = $codeAgg
    latencyMs = [ordered]@{
        p50 = Get-Percentile -Values $allLatencies -P 0.50
        p95 = Get-Percentile -Values $allLatencies -P 0.95
        p99 = Get-Percentile -Values $allLatencies -P 0.99
    }
}

# P7-S2 residual close attempt (FAIL -> SENT)
$eventId = "P7S2_MQ_FAIL_20260226104148"
$eventBefore = Invoke-Api -Method "GET" -Url "$BaseUrl/admin/ops/outbox/event/$eventId" -Headers $hAdmin
$jsonValidBefore = [int](Get-MySqlScalar "SELECT JSON_VALID(payload_json) FROM message_outbox WHERE event_id='$eventId' LIMIT 1;")

if ($jsonValidBefore -eq 0) {
    $repairSql = @"
UPDATE message_outbox
SET payload_json = JSON_OBJECT(
  'eventId','P7S2_MQ_FAIL_20260226104148',
  'eventType','ORDER_STATUS_CHANGED',
  'routingKey','order.status.changed',
  'bizId',900055,
  'occurredAt','2026-03-04T10:30:00',
  'payload',JSON_OBJECT('orderId',900055),
  'version',1
)
WHERE event_id='P7S2_MQ_FAIL_20260226104148';
"@
    Invoke-MySqlQuery $repairSql | Out-Null
}

$jsonValidAfterFix = [int](Get-MySqlScalar "SELECT JSON_VALID(payload_json) FROM message_outbox WHERE event_id='$eventId' LIMIT 1;")
$triggerNow = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/ops/outbox/event/$eventId/trigger-now" -Headers $hAdmin
$publishOnce = Invoke-Api -Method "POST" -Url "$BaseUrl/admin/ops/outbox/publish-once?limit=10" -Headers $hAdmin
Start-Sleep -Seconds 2
$eventAfter = Invoke-Api -Method "GET" -Url "$BaseUrl/admin/ops/outbox/event/$eventId" -Headers $hAdmin

$evidence.p7_s2 = [ordered]@{
    eventId = $eventId
    jsonValidBefore = $jsonValidBefore
    eventBefore = Api-Summary $eventBefore
    jsonValidAfterFix = $jsonValidAfterFix
    triggerNow = Api-Summary $triggerNow
    publishOnce = Api-Summary $publishOnce
    eventAfter = Api-Summary $eventAfter
}

# Log samples
if (Test-Path $LogFile) {
    $auditLines = @(
        Select-String -Path $LogFile -Pattern "AUDIT auditId=" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Line
    )
    $idempotentLines = @(
        Select-String -Path $LogFile -Pattern "idemKey=|consumer=" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Line
    )

    $evidence.log_samples["audit_actions"] = @(
        $auditLines |
            Where-Object { $_ -match "action=(USER_LOGIN|ADMIN_LOGIN|ORDER_PAY|ORDER_CANCEL|PAYMENT_CALLBACK|USER_BAN|USER_UNBAN)" } |
            Select-Object -Last 30
    )
    $evidence.log_samples["idempotent_hits"] = @(
        $idempotentLines |
            Where-Object { $_ -match "payOrder|paymentCallback|createShipTimeoutTask|consumer=" } |
            Select-Object -Last 30
    )
}

# DoD booleans
$dupAllZero = ($dupChecks.ship_timeout -eq 0 -and $dupChecks.ship_reminder -eq 0 -and $dupChecks.refund_task -eq 0 -and $dupChecks.refund_idempotency_key -eq 0 -and $dupChecks.mq_consume_log -eq 0 -and $dupChecks.points_ledger -eq 0)
$hasIdemLog = ($evidence.log_samples.idempotent_hits.Count -gt 0)

$lv1Blocked = (($lv1Create.response.code -ne 1) -and ([string]$lv1Create.response.msg -match "LV1"))
$lv2First3Ok = $true
for ($i = 0; $i -lt 3; $i++) {
    if ([int]$lv2Attempts[$i].result.code -ne 1) {
        $lv2First3Ok = $false
    }
}
$lv2FourthBlocked = ([int]$lv2Attempts[3].result.code -ne 1) -and ([string]$lv2Attempts[3].result.msg -match "LV2")
$lv3Passed = ([int]$lv3Create.response.code -eq 1)
$outboxRecovered = (([string]$eventAfter.response.data.status) -eq "SENT")

$evidence.dod = [ordered]@{
    p1_repeat_no_duplicate = $dupAllZero
    p1_idempotent_log_searchable = $hasIdemLog
    p4_audit_chain_searchable = ($evidence.log_samples.audit_actions.Count -gt 0)
    p5_lv1_blocked = $lv1Blocked
    p5_lv2_quota_enforced = ($lv2First3Ok -and $lv2FourthBlocked)
    p5_lv3_plus_allowed = $lv3Passed
    p6_load_data_archived = ($evidence.p6_s2.totalRequests -gt 0)
    p7_fail_to_sent_recovered = $outboxRecovered
}

$json = $evidence | ConvertTo-Json -Depth 30
$json | Out-File -FilePath $outputPath -Encoding utf8

Write-Host "Day18 close-loop evidence written:"
Write-Host $outputPath
