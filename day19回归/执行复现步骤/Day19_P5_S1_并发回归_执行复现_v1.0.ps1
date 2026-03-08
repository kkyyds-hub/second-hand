[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$MySqlHost = "localhost",
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = "secondhand2",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "1234",
    [long]$BuyerUserId = 1,
    [long]$SellerUserId = 2,
    [long]$AdminUserId = 5,
    [long]$BanTargetUserId = 6,
    [int]$Concurrency = 50,
    [int]$TimeoutSeconds = 20,
    [string]$OutputDir = "",
    [switch]$SkipCleanup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::UTF8
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path (Split-Path -Parent $PSScriptRoot) "执行记录"
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

$script:RunStamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$script:RunPrefix = "DAY19-P5-S1-$([DateTime]::Now.ToString('yyyyMMddHHmmss'))"
$script:MySqlCli = $null
$script:CreatedProductIds = New-Object System.Collections.Generic.List[long]
$script:CreatedOrderIds = New-Object System.Collections.Generic.List[long]
$script:ScenarioNotes = New-Object System.Collections.Generic.List[string]
$script:WarningNotes = New-Object System.Collections.Generic.List[string]

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

function Ensure-MySqlCli {
    if ($script:MySqlCli) {
        return
    }
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -eq $mysqlCommand) {
        throw "mysql CLI not found"
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

function Invoke-DbNonQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql
    )
    [void](Invoke-DbText -Sql $Sql)
}

function Convert-ToSqlLiteral {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) {
        return "NULL"
    }
    if ($Value -is [bool]) {
        return ($(if ($Value) { "1" } else { "0" }))
    }
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [decimal] -or $Value -is [double]) {
        return [string]$Value
    }
    return "'" + ([string]$Value).Replace("'", "''") + "'"
}

function New-JwtToken {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Secret,
        [Parameter(Mandatory = $true)]
        [hashtable]$Claims,
        [long]$TtlMs = 7200000
    )

    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payload = [ordered]@{}
    foreach ($key in $Claims.Keys) {
        $payload[$key] = $Claims[$key]
    }
    $payload['exp'] = [int64]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + [Math]::Floor($TtlMs / 1000))

    function Convert-ToBase64Url([byte[]]$Bytes) {
        return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }

    $headerBase64 = Convert-ToBase64Url ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
    $payloadJson = $payload | ConvertTo-Json -Compress
    $payloadBase64 = Convert-ToBase64Url ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$headerBase64.$payloadBase64"

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($unsigned))
    } finally {
        $hmac.Dispose()
    }
    $signatureBase64 = Convert-ToBase64Url $signatureBytes
    return "$unsigned.$signatureBase64"
}

function Invoke-ApiRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [hashtable]$Headers,
        [AllowNull()][object]$Body,
        [int]$TimeoutSec = 20
    )

    $startedAt = Get-Date
    $httpStatus = $null
    $content = $null
    $errorType = $null
    $errorMessage = $null

    try {
        if ($Method -eq "GET") {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec $TimeoutSec
        } elseif ($null -eq $Body) {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -TimeoutSec $TimeoutSec
        } else {
            $jsonBody = $Body | ConvertTo-Json -Compress -Depth 10
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec $TimeoutSec
        }
        $httpStatus = [int]$resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $httpStatus = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
            try {
                $content = $reader.ReadToEnd()
            } finally {
                $reader.Close()
            }
        }
        $errorType = $_.Exception.GetType().Name
        $errorMessage = $_.Exception.Message
    }

    $endedAt = Get-Date
    $parsed = ConvertFrom-JsonSafe -Text $content
    $businessCode = if ($null -ne $parsed -and $null -ne $parsed.code) { [int]$parsed.code } else { $null }
    $businessMsg = if ($null -ne $parsed) {
        if ($null -ne $parsed.data -and ($parsed.data -is [string])) {
            [string]$parsed.data
        } elseif ($null -ne $parsed.msg) {
            [string]$parsed.msg
        } else {
            $null
        }
    } else {
        $null
    }
    $success = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $businessCode -eq 1)

    return [pscustomobject]@{
        httpStatus = $httpStatus
        businessCode = $businessCode
        businessMsg = $businessMsg
        success = $success
        elapsedMs = [Math]::Round(($endedAt - $startedAt).TotalMilliseconds, 2)
        errorType = $errorType
        errorMessage = $errorMessage
        rawBody = $content
        body = $parsed
    }
}

if (-not ('ConcurrentHttpSpec' -as [type])) {
    Add-Type -Language CSharp -ReferencedAssemblies @('System.dll', 'System.Core.dll', 'System.Net.Http.dll') -TypeDefinition @"
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;

public class ConcurrentHttpSpec {
    public int Index { get; set; }
    public string Method { get; set; }
    public string Url { get; set; }
    public string BodyJson { get; set; }
    public Dictionary<string, string> Headers { get; set; }
}

public class ConcurrentHttpResult {
    public int Index { get; set; }
    public int HttpStatus { get; set; }
    public string Content { get; set; }
    public double ElapsedMs { get; set; }
    public string ErrorType { get; set; }
    public string ErrorMessage { get; set; }
}

public static class ConcurrentHttpRunner {
    public static List<ConcurrentHttpResult> Run(List<ConcurrentHttpSpec> specs, int timeoutSeconds) {
        var results = new ConcurrentBag<ConcurrentHttpResult>();
        Parallel.ForEach(specs, new ParallelOptions { MaxDegreeOfParallelism = specs.Count }, spec => {
            var started = DateTime.UtcNow;
            int status = 0;
            string content = null;
            string errorType = null;
            string errorMessage = null;
            try {
                using (var client = new HttpClient()) {
                    client.Timeout = TimeSpan.FromSeconds(timeoutSeconds);
                    using (var request = new HttpRequestMessage(new HttpMethod(spec.Method), spec.Url)) {
                        if (spec.Headers != null) {
                            foreach (var kv in spec.Headers) {
                                request.Headers.TryAddWithoutValidation(kv.Key, kv.Value);
                            }
                        }
                        if (!string.IsNullOrWhiteSpace(spec.BodyJson)) {
                            request.Content = new StringContent(spec.BodyJson, Encoding.UTF8, "application/json");
                        }
                        using (var response = client.SendAsync(request).GetAwaiter().GetResult()) {
                            status = (int)response.StatusCode;
                            content = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();
                        }
                    }
                }
            } catch (Exception ex) {
                errorType = ex.GetType().Name;
                errorMessage = ex.Message;
            }
            var elapsed = Math.Round((DateTime.UtcNow - started).TotalMilliseconds, 2);
            results.Add(new ConcurrentHttpResult {
                Index = spec.Index,
                HttpStatus = status,
                Content = content,
                ElapsedMs = elapsed,
                ErrorType = errorType,
                ErrorMessage = errorMessage
            });
        });
        return results.OrderBy(r => r.Index).ToList();
    }
}
"@
}

function Invoke-ConcurrentApiRequests {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [scriptblock]$UriFactory,
        [Parameter(Mandatory = $true)]
        [int]$Count,
        [hashtable]$Headers,
        [scriptblock]$BodyFactory
    )

    $specs = New-Object 'System.Collections.Generic.List[ConcurrentHttpSpec]'
    for ($i = 1; $i -le $Count; $i++) {
        $uri = & $UriFactory $i
        $body = if ($BodyFactory) { & $BodyFactory $i } else { $null }
        $bodyJson = if ($null -eq $body) { $null } else { $body | ConvertTo-Json -Compress -Depth 10 }
        $netHeaders = New-Object 'System.Collections.Generic.Dictionary[string,string]'
        if ($Headers) {
            foreach ($key in $Headers.Keys) {
                $netHeaders.Add([string]$key, [string]$Headers[$key])
            }
        }
        $spec = New-Object ConcurrentHttpSpec
        $spec.Index = $i
        $spec.Method = $Method
        $spec.Url = $uri
        $spec.BodyJson = $bodyJson
        $spec.Headers = $netHeaders
        [void]$specs.Add($spec)
    }

    $results = [ConcurrentHttpRunner]::Run($specs, $TimeoutSeconds)
    return $results | ForEach-Object {
        $parsed = ConvertFrom-JsonSafe -Text $_.Content
        $businessCode = if ($null -ne $parsed -and $null -ne $parsed.code) { [int]$parsed.code } else { $null }
        $businessMsg = if ($null -ne $parsed) {
            if ($null -ne $parsed.data -and ($parsed.data -is [string])) {
                [string]$parsed.data
            } elseif ($null -ne $parsed.msg) {
                [string]$parsed.msg
            } else {
                $null
            }
        } else {
            $null
        }
        [pscustomobject]@{
            index = $_.Index
            httpStatus = if ($_.HttpStatus -eq 0) { $null } else { $_.HttpStatus }
            businessCode = $businessCode
            businessMsg = $businessMsg
            success = ($_.HttpStatus -ge 200 -and $_.HttpStatus -lt 300 -and $businessCode -eq 1)
            elapsedMs = $_.ElapsedMs
            errorType = $_.ErrorType
            errorMessage = $_.ErrorMessage
            body = $parsed
            rawBody = $_.Content
        }
    }
}

function Summarize-Responses {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IEnumerable]$Results
    )
    $items = @($Results)
    $messageCounts = @{}
    foreach ($item in $items) {
        $key = if ([string]::IsNullOrWhiteSpace($item.businessMsg)) {
            if ($item.success) { "success(no-message)" } elseif ($item.errorType) { "transport-error" } else { "empty" }
        } else {
            $item.businessMsg
        }
        if (-not $messageCounts.ContainsKey($key)) {
            $messageCounts[$key] = 0
        }
        $messageCounts[$key]++
    }
    $sortedMessages = $messageCounts.GetEnumerator() | Sort-Object Name | ForEach-Object {
        [pscustomobject]@{
            message = $_.Key
            count = $_.Value
        }
    }
    $elapsed = $items | Where-Object { $null -ne $_.elapsedMs } | ForEach-Object { [double]$_.elapsedMs }
    $avgElapsed = if ($elapsed.Count -gt 0) { [Math]::Round((($elapsed | Measure-Object -Average).Average), 2) } else { $null }
    $p95Elapsed = if ($elapsed.Count -gt 0) {
        $sorted = @($elapsed | Sort-Object)
        $idx = [Math]::Ceiling($sorted.Count * 0.95) - 1
        if ($idx -lt 0) { $idx = 0 }
        [Math]::Round([double]$sorted[$idx], 2)
    } else {
        $null
    }

    return [pscustomobject]@{
        total = $items.Count
        successCount = (@($items | Where-Object { $_.success })).Count
        failureCount = (@($items | Where-Object { -not $_.success })).Count
        avgElapsedMs = $avgElapsed
        p95ElapsedMs = $p95Elapsed
        messageCounts = $sortedMessages
    }
}

function New-TestProduct {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScenarioCode,
        [Parameter(Mandatory = $true)]
        [decimal]$Price
    )

    $title = "$($script:RunPrefix)-$ScenarioCode"
    $sql = @(
        "INSERT INTO products (",
        "    owner_id, title, description, price, images, category, status, view_count, reason, is_deleted, create_time, update_time",
        ") VALUES (",
        "    $SellerUserId,",
        "    $(Convert-ToSqlLiteral $title),",
        "    $(Convert-ToSqlLiteral 'Day19 P5-S1 并发回归样本'),",
        "    $Price,",
        "    $(Convert-ToSqlLiteral 'https://example.com/day19-p5-s1.png'),",
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
    [void]$script:CreatedProductIds.Add($productId)
    return [pscustomobject]@{
        productId = $productId
        title = $title
        ownerId = $SellerUserId
        price = $Price
    }
}

function New-PendingOrder {
    param(
        [Parameter(Mandatory = $true)]
        [long]$ProductId,
        [Parameter(Mandatory = $true)]
        [hashtable]$UserHeaders
    )

    $request = @{
        productId = $ProductId
        shippingAddress = "上海市浦东新区 Day19 并发回归样本路 100 号"
        quantity = 1
    }
    $response = Invoke-ApiRequest -Method "POST" -Uri "$BaseUrl/user/orders" -Headers $UserHeaders -Body $request -TimeoutSec $TimeoutSeconds
    if (-not $response.success -or $null -eq $response.body -or $null -eq $response.body.data) {
        throw "create order failed: $($response.rawBody)"
    }

    $data = $response.body.data
    $orderId = [long]$data.orderId
    [void]$script:CreatedOrderIds.Add($orderId)
    return [pscustomobject]@{
        orderId = $orderId
        orderNo = [string]$data.orderNo
        status = [string]$data.status
        totalAmount = [decimal]$data.totalAmount
    }
}

function Get-OrderMetrics {
    param(
        [Parameter(Mandatory = $true)]
        [long]$OrderId
    )

    $sql = @(
        "SELECT CONCAT_WS('|',",
        "    o.status,",
        "    IFNULL(o.cancel_reason, ''),",
        "    IFNULL(CAST((SELECT COUNT(*) FROM order_ship_timeout_task t WHERE t.order_id = o.id) AS CHAR), '0'),",
        "    IFNULL(CAST((SELECT COUNT(*) FROM order_refund_task r WHERE r.order_id = o.id) AS CHAR), '0'),",
        "    IFNULL(CAST((SELECT COUNT(*) FROM message_outbox m WHERE m.biz_id = o.id AND m.event_type = 'ORDER_PAID') AS CHAR), '0'),",
        "    IFNULL(CAST((SELECT COUNT(*) FROM message_outbox m WHERE m.biz_id = o.id AND m.event_type = 'ORDER_CREATED') AS CHAR), '0'),",
        "    IFNULL((SELECT status FROM order_ship_timeout_task t WHERE t.order_id = o.id LIMIT 1), ''),",
        "    IFNULL((SELECT CAST(retry_count AS CHAR) FROM order_ship_timeout_task t WHERE t.order_id = o.id LIMIT 1), '0'),",
        "    IFNULL((SELECT status FROM order_refund_task r WHERE r.order_id = o.id LIMIT 1), ''),",
        "    IFNULL((SELECT CAST(product_id AS CHAR) FROM order_items i WHERE i.order_id = o.id LIMIT 1), '')",
        ")",
        "FROM orders o",
        "WHERE o.id = $OrderId",
        "LIMIT 1;"
    ) -join "`n"
    $raw = Invoke-DbScalar -Sql $sql
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $null
    }
    $parts = $raw -split '\|', 10
    return [pscustomobject]@{
        orderStatus = $parts[0]
        cancelReason = $parts[1]
        shipTimeoutTaskCount = [int]$parts[2]
        refundTaskCount = [int]$parts[3]
        orderPaidOutboxCount = [int]$parts[4]
        orderCreatedOutboxCount = [int]$parts[5]
        shipTimeoutTaskStatus = $parts[6]
        shipTimeoutRetryCount = [int]$parts[7]
        refundTaskStatus = $parts[8]
        productId = if ([string]::IsNullOrWhiteSpace($parts[9])) { $null } else { [long]$parts[9] }
    }
}

function Get-ProductStatus {
    param([long]$ProductId)
    return Invoke-DbScalar -Sql "SELECT status FROM products WHERE id = $ProductId LIMIT 1;"
}

function Get-UserStatus {
    param([long]$UserId)
    return Invoke-DbScalar -Sql "SELECT status FROM users WHERE id = $UserId LIMIT 1;"
}

function Reset-BanTargetToActive {
    Invoke-DbNonQuery -Sql (
        @(
            "UPDATE users",
            "SET status = 'active', update_time = NOW()",
            "WHERE id = $BanTargetUserId;",
            "UPDATE user_bans",
            "SET end_time = NOW()",
            "WHERE user_id = $BanTargetUserId",
            "  AND end_time IS NULL;"
        ) -join "`n"
    )
}

function Force-BanTargetToBanned {
    Invoke-DbNonQuery -Sql (
        @(
            "UPDATE users",
            "SET status = 'banned', update_time = NOW()",
            "WHERE id = $BanTargetUserId;",
            "UPDATE user_bans",
            "SET end_time = NOW()",
            "WHERE user_id = $BanTargetUserId",
            "  AND end_time IS NULL;"
        ) -join "`n"
    )
}

function Mark-ShipTimeoutTaskDueNow {
    param(
        [Parameter(Mandatory = $true)]
        [long]$OrderId
    )
    Invoke-DbNonQuery -Sql (
        @(
            "UPDATE order_ship_timeout_task t",
            "JOIN orders o ON o.id = t.order_id",
            "SET t.deadline_time = o.pay_time,",
            "    t.next_retry_time = NULL,",
            "    t.last_error = NULL,",
            "    t.update_time = NOW()",
            "WHERE t.order_id = $OrderId;"
        ) -join "`n"
    )
}

function Get-ShipTimeoutTaskAdminView {
    param([long]$OrderId)
    $resp = Invoke-ApiRequest -Method "GET" -Uri "$BaseUrl/admin/ops/tasks/ship-timeout?orderId=$OrderId&limit=5" -Headers $script:AdminHeaders -Body $null -TimeoutSec $TimeoutSeconds
    if (-not $resp.success -or $null -eq $resp.body -or $null -eq $resp.body.data) {
        return $null
    }
    $list = @($resp.body.data.list)
    if ($list.Count -eq 0) {
        return $null
    }
    return $list[0]
}

function Cleanup-TestArtifacts {
    if ($SkipCleanup) {
        [void]$script:WarningNotes.Add("执行时跳过清理，合成样本保留在库中。")
        return
    }

    if ($script:CreatedOrderIds.Count -eq 0 -and $script:CreatedProductIds.Count -eq 0) {
        return
    }

    $orderIdList = if ($script:CreatedOrderIds.Count -gt 0) {
        ($script:CreatedOrderIds | Sort-Object -Unique) -join ','
    } else {
        ""
    }
    $productIdList = if ($script:CreatedProductIds.Count -gt 0) {
        ($script:CreatedProductIds | Sort-Object -Unique) -join ','
    } else {
        ""
    }

    $cleanupSql = New-Object System.Text.StringBuilder
    if ($orderIdList) {
        [void]$cleanupSql.AppendLine("DELETE FROM message_outbox WHERE biz_id IN ($orderIdList) AND event_type IN ('ORDER_CREATED', 'ORDER_PAID');")
        [void]$cleanupSql.AppendLine("DELETE FROM order_refund_task WHERE order_id IN ($orderIdList);")
        [void]$cleanupSql.AppendLine("DELETE FROM order_ship_timeout_task WHERE order_id IN ($orderIdList);")
        [void]$cleanupSql.AppendLine("DELETE FROM order_items WHERE order_id IN ($orderIdList);")
        [void]$cleanupSql.AppendLine("DELETE FROM orders WHERE id IN ($orderIdList);")
    }
    if ($productIdList) {
        [void]$cleanupSql.AppendLine("DELETE FROM product_status_audit_log WHERE product_id IN ($productIdList);")
        [void]$cleanupSql.AppendLine("DELETE FROM products WHERE id IN ($productIdList);")
    }
    [void]$cleanupSql.AppendLine((
        @(
            "UPDATE users",
            "SET status = 'active', update_time = NOW()",
            "WHERE id = $BanTargetUserId;",
            "UPDATE user_bans",
            "SET end_time = NOW()",
            "WHERE user_id = $BanTargetUserId",
            "  AND end_time IS NULL;"
        ) -join "`n"
    ))
    Invoke-DbNonQuery -Sql $cleanupSql.ToString()
}

function Get-CleanupMetrics {
    $orderIds = if ($script:CreatedOrderIds.Count -gt 0) { ($script:CreatedOrderIds | Sort-Object -Unique) -join ',' } else { "" }
    $productIds = if ($script:CreatedProductIds.Count -gt 0) { ($script:CreatedProductIds | Sort-Object -Unique) -join ',' } else { "" }

    return [pscustomobject]@{
        ordersRemaining = if ($orderIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM orders WHERE id IN ($orderIds);") } else { 0 }
        orderItemsRemaining = if ($orderIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM order_items WHERE order_id IN ($orderIds);") } else { 0 }
        shipTimeoutTasksRemaining = if ($orderIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM order_ship_timeout_task WHERE order_id IN ($orderIds);") } else { 0 }
        refundTasksRemaining = if ($orderIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM order_refund_task WHERE order_id IN ($orderIds);") } else { 0 }
        outboxRemaining = if ($orderIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM message_outbox WHERE biz_id IN ($orderIds) AND event_type IN ('ORDER_CREATED', 'ORDER_PAID');") } else { 0 }
        productsRemaining = if ($productIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM products WHERE id IN ($productIds);") } else { 0 }
        productAuditRemaining = if ($productIds) { [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM product_status_audit_log WHERE product_id IN ($productIds);") } else { 0 }
        banTargetStatus = Get-UserStatus -UserId $BanTargetUserId
        activeBanRows = [int](Invoke-DbScalar -Sql "SELECT COUNT(*) FROM user_bans WHERE user_id = $BanTargetUserId AND end_time IS NULL;")
    }
}

$script:AdminHeaders = @{ token = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/dev/token/safe-token" -TimeoutSec 10).Content.Trim('"') }
$script:UserHeaders = @{ authentication = (New-JwtToken -Secret 'dev-user-secret-change-me' -Claims @{ userId = $BuyerUserId }) }

$executionStartedAt = Get-Date
$scenarios = New-Object System.Collections.Generic.List[object]

try {
    Reset-BanTargetToActive

    $payProduct = New-TestProduct -ScenarioCode "PAY" -Price 101.00
    $payOrder = New-PendingOrder -ProductId $payProduct.productId -UserHeaders $script:UserHeaders
    $payResults = Invoke-ConcurrentApiRequests -Method "POST" -UriFactory { param($i) "$BaseUrl/user/orders/$($payOrder.orderId)/pay" } -Count $Concurrency -Headers $script:UserHeaders
    $paySummary = Summarize-Responses -Results $payResults
    $payPrimaryCount = @($payResults | Where-Object { $_.businessMsg -eq '支付成功' }).Count
    $payDb = Get-OrderMetrics -OrderId $payOrder.orderId
    [void]$scenarios.Add([pscustomobject]@{
        scenarioCode = "S1_DUPLICATE_PAY"
        title = "同订单重复支付"
        order = $payOrder
        product = $payProduct
        requestCount = $Concurrency
        summary = $paySummary
        splitRatio = [pscustomobject]@{
            primarySuccessRatio = [Math]::Round(($payPrimaryCount * 100.0) / [Math]::Max(1, $Concurrency), 2)
            branchRatio = [Math]::Round((($Concurrency - $payPrimaryCount) * 100.0) / [Math]::Max(1, $Concurrency), 2)
        }
        db = $payDb
        passed = ($payDb.orderStatus -eq 'paid' -and $payDb.shipTimeoutTaskCount -eq 1 -and $payDb.orderPaidOutboxCount -eq 0)
        responseSamples = $paySummary.messageCounts
    })

    $callbackProduct = New-TestProduct -ScenarioCode "CALLBACK" -Price 102.00
    $callbackOrder = New-PendingOrder -ProductId $callbackProduct.productId -UserHeaders $script:UserHeaders
    $callbackTradeNo = "$($script:RunPrefix)-CB-TRADE"
    $callbackResults = Invoke-ConcurrentApiRequests -Method "POST" -UriFactory { param($i) "$BaseUrl/payment/callback" } -Count $Concurrency -Headers @{} -BodyFactory {
        param($i)
        @{
            channel = "mock"
            orderNo = $callbackOrder.orderNo
            tradeNo = $callbackTradeNo
            amount = [decimal]$callbackOrder.totalAmount
            status = "SUCCESS"
            timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
            sign = "day19-p5-s1-sign"
        }
    }
    $callbackSummary = Summarize-Responses -Results $callbackResults
    $callbackPrimaryCount = @($callbackResults | Where-Object { $_.businessMsg -eq '支付回调处理成功' }).Count
    $callbackDb = Get-OrderMetrics -OrderId $callbackOrder.orderId
    [void]$scenarios.Add([pscustomobject]@{
        scenarioCode = "S2_DUPLICATE_CALLBACK"
        title = "同订单重复成功回调"
        order = $callbackOrder
        product = $callbackProduct
        requestCount = $Concurrency
        summary = $callbackSummary
        splitRatio = [pscustomobject]@{
            primarySuccessRatio = [Math]::Round(($callbackPrimaryCount * 100.0) / [Math]::Max(1, $Concurrency), 2)
            branchRatio = [Math]::Round((($Concurrency - $callbackPrimaryCount) * 100.0) / [Math]::Max(1, $Concurrency), 2)
        }
        db = $callbackDb
        passed = ($callbackDb.orderStatus -eq 'paid' -and $callbackDb.shipTimeoutTaskCount -eq 1 -and $callbackDb.orderPaidOutboxCount -eq 1)
        responseSamples = $callbackSummary.messageCounts
    })

    Reset-BanTargetToActive
    $banResults = Invoke-ConcurrentApiRequests -Method "PUT" -UriFactory { param($i) "$BaseUrl/admin/user/$BanTargetUserId/ban" } -Count $Concurrency -Headers $script:AdminHeaders
    $banSummary = Summarize-Responses -Results $banResults
    $banPrimaryCount = @($banResults | Where-Object { $_.businessMsg -eq '用户封禁成功' }).Count
    $banStatus = Get-UserStatus -UserId $BanTargetUserId
    [void]$scenarios.Add([pscustomobject]@{
        scenarioCode = "S3_DUPLICATE_BAN"
        title = "同用户重复封禁"
        targetUserId = $BanTargetUserId
        requestCount = $Concurrency
        summary = $banSummary
        splitRatio = [pscustomobject]@{
            primarySuccessRatio = [Math]::Round(($banPrimaryCount * 100.0) / [Math]::Max(1, $Concurrency), 2)
            branchRatio = [Math]::Round((($Concurrency - $banPrimaryCount) * 100.0) / [Math]::Max(1, $Concurrency), 2)
        }
        db = [pscustomobject]@{
            userStatus = $banStatus
        }
        passed = ($banStatus -eq 'banned')
        responseSamples = $banSummary.messageCounts
    })

    Force-BanTargetToBanned
    $unbanResults = Invoke-ConcurrentApiRequests -Method "PUT" -UriFactory { param($i) "$BaseUrl/admin/user/$BanTargetUserId/unban" } -Count $Concurrency -Headers $script:AdminHeaders
    $unbanSummary = Summarize-Responses -Results $unbanResults
    $unbanPrimaryCount = @($unbanResults | Where-Object { $_.businessMsg -eq '用户解封成功' }).Count
    $unbanStatus = Get-UserStatus -UserId $BanTargetUserId
    [void]$scenarios.Add([pscustomobject]@{
        scenarioCode = "S4_DUPLICATE_UNBAN"
        title = "同用户重复解封"
        targetUserId = $BanTargetUserId
        requestCount = $Concurrency
        summary = $unbanSummary
        splitRatio = [pscustomobject]@{
            primarySuccessRatio = [Math]::Round(($unbanPrimaryCount * 100.0) / [Math]::Max(1, $Concurrency), 2)
            branchRatio = [Math]::Round((($Concurrency - $unbanPrimaryCount) * 100.0) / [Math]::Max(1, $Concurrency), 2)
        }
        db = [pscustomobject]@{
            userStatus = $unbanStatus
        }
        passed = ($unbanStatus -eq 'active')
        responseSamples = $unbanSummary.messageCounts
    })

    $taskProduct = New-TestProduct -ScenarioCode "SHIPTIMEOUT" -Price 103.00
    $taskOrder = New-PendingOrder -ProductId $taskProduct.productId -UserHeaders $script:UserHeaders
    $taskPayResponse = Invoke-ApiRequest -Method "POST" -Uri "$BaseUrl/user/orders/$($taskOrder.orderId)/pay" -Headers $script:UserHeaders -Body $null -TimeoutSec $TimeoutSeconds
    if (-not $taskPayResponse.success) {
        throw "prepare ship-timeout order failed: $($taskPayResponse.rawBody)"
    }
    Mark-ShipTimeoutTaskDueNow -OrderId $taskOrder.orderId
    Start-Sleep -Milliseconds 200
    $taskResults = Invoke-ConcurrentApiRequests -Method "POST" -UriFactory { param($i) "$BaseUrl/admin/ops/tasks/ship-timeout/run-once?limit=1" } -Count $Concurrency -Headers $script:AdminHeaders
    $taskSummary = Summarize-Responses -Results $taskResults
    $taskDb = Get-OrderMetrics -OrderId $taskOrder.orderId
    $taskAdminView = Get-ShipTimeoutTaskAdminView -OrderId $taskOrder.orderId
    $taskProductStatus = if ($null -ne $taskDb.productId) { Get-ProductStatus -ProductId $taskDb.productId } else { $null }
    $taskRunSuccessCount = @(
        $taskResults | Where-Object {
            $_.success -and
            $null -ne $_.body -and
            $null -ne $_.body.data -and
            $null -ne $_.body.data.success -and
            [int]$_.body.data.success -gt 0
        }
    ).Count
    [void]$scenarios.Add([pscustomobject]@{
        scenarioCode = "S5_SHIP_TIMEOUT_TASK_RACE"
        title = "发货超时任务并发抢同一条任务"
        order = $taskOrder
        product = $taskProduct
        requestCount = $Concurrency
        summary = $taskSummary
        splitRatio = [pscustomobject]@{
            primarySuccessRatio = [Math]::Round(($taskRunSuccessCount * 100.0) / [Math]::Max(1, $Concurrency), 2)
            branchRatio = [Math]::Round((($Concurrency - $taskRunSuccessCount) * 100.0) / [Math]::Max(1, $Concurrency), 2)
        }
        db = [pscustomobject]@{
            orderStatus = $taskDb.orderStatus
            cancelReason = $taskDb.cancelReason
            shipTimeoutTaskCount = $taskDb.shipTimeoutTaskCount
            shipTimeoutTaskStatus = $taskDb.shipTimeoutTaskStatus
            shipTimeoutRetryCount = $taskDb.shipTimeoutRetryCount
            refundTaskCount = $taskDb.refundTaskCount
            refundTaskStatus = $taskDb.refundTaskStatus
            productStatus = $taskProductStatus
            adminTaskView = $taskAdminView
        }
        passed = (
            $taskDb.orderStatus -eq 'cancelled' -and
            $taskDb.cancelReason -eq 'ship_timeout' -and
            $taskDb.shipTimeoutTaskCount -eq 1 -and
            $taskDb.shipTimeoutTaskStatus -eq 'DONE' -and
            $taskDb.refundTaskCount -eq 1 -and
            $taskProductStatus -eq 'on_sale'
        )
        responseSamples = $taskSummary.messageCounts
    })
} finally {
    Cleanup-TestArtifacts
}

$cleanupMetrics = Get-CleanupMetrics
$executionEndedAt = Get-Date
$overallPassed = (@($scenarios | Where-Object { -not $_.passed }).Count -eq 0)

$dynamicResult = [pscustomobject]@{
    step = "Day19-P5-S1"
    title = "核心并发链路回归"
    executedAt = $executionEndedAt.ToString("yyyy-MM-dd HH:mm:ss")
    baseUrl = $BaseUrl
    runPrefix = $script:RunPrefix
    concurrency = $Concurrency
    buyerUserId = $BuyerUserId
    sellerUserId = $SellerUserId
    adminUserId = $AdminUserId
    banTargetUserId = $BanTargetUserId
    startedAt = $executionStartedAt.ToString("yyyy-MM-dd HH:mm:ss")
    endedAt = $executionEndedAt.ToString("yyyy-MM-dd HH:mm:ss")
    elapsedMs = [Math]::Round(($executionEndedAt - $executionStartedAt).TotalMilliseconds, 2)
    overallPassed = $overallPassed
    scenarios = $scenarios
    cleanup = $cleanupMetrics
    notes = $script:ScenarioNotes
    warnings = $script:WarningNotes
}

$jsonPath = Join-Path $OutputDir "Day19_P5_S1_动态结果_$($script:RunStamp).json"
$dynamicResult | ConvertTo-Json -Depth 10 | Out-File -FilePath $jsonPath

Write-Host "DONE"
Write-Host "DynamicResult=$jsonPath"
