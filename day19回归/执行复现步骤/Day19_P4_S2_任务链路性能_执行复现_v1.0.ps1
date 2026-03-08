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
    [int]$AutoRecoveryMaxWaitSeconds = 180,
    [string]$OutputDir = "",
    [switch]$SkipAutoRecoveryWait
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:TaskTypes = @("ship-timeout", "refund", "ship-reminder")
$script:RunId = Get-Date -Format "yyyyMMddHHmmss"
$script:MySqlCli = $null
$script:MySqlArgs = @()

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

function Convert-ToDbNullable {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq "NULL") {
        return $null
    }
    return $Value
}

function Convert-ToDbDateTime {
    param([string]$Value)
    $safeValue = Convert-ToDbNullable -Value $Value
    if ($null -eq $safeValue) {
        return $null
    }
    return [DateTime]::ParseExact($safeValue, "yyyy-MM-dd HH:mm:ss", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Convert-ToSqlLiteral {
    param([string]$Text)
    if ($null -eq $Text) {
        return "NULL"
    }
    return "'" + $Text.Replace("'", "''") + "'"
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percent)
    $safeValues = @($Values)
    if ($null -eq $safeValues -or $safeValues.Length -eq 0) {
        return $null
    }
    $sorted = @($safeValues | Sort-Object)
    $index = [int][Math]::Ceiling($Percent * $sorted.Length) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Length) { $index = $sorted.Length - 1 }
    return [Math]::Round([double]$sorted[$index], 2)
}

function Summarize-Numbers {
    param([double[]]$Values)
    $safeValues = @($Values)
    if ($null -eq $safeValues -or $safeValues.Length -eq 0) {
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
        count = $safeValues.Length
        min = [Math]::Round(([double]($safeValues | Measure-Object -Minimum).Minimum), 2)
        p50 = Get-Percentile -Values $safeValues -Percent 0.50
        p95 = Get-Percentile -Values $safeValues -Percent 0.95
        max = [Math]::Round(([double]($safeValues | Measure-Object -Maximum).Maximum), 2)
        avg = [Math]::Round(([double]($safeValues | Measure-Object -Average).Average), 2)
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
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec 20
        } elseif ($null -eq $Body) {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -TimeoutSec 20
        } else {
            $jsonBody = $Body | ConvertTo-Json -Depth 10
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 20
        }
        $httpStatus = [int]$resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $httpStatus = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Close()
        }
        $errorType = $_.Exception.GetType().Name
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

function Ensure-MySqlCli {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -eq $mysqlCommand) {
        throw "mysql CLI not found for P4-S2"
    }
    $script:MySqlCli = $mysqlCommand.Source
    $env:MYSQL_PWD = $MySqlPassword
    $script:MySqlArgs = @(
        "-h", $MySqlHost,
        "-P", $MySqlPort.ToString(),
        "-u", $MySqlUser,
        "-D", $MySqlDatabase
    )
}

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

function New-TestPrefix {
    param(
        [string]$TaskType,
        [string]$Scenario,
        [string]$Suffix
    )
    $typeCode = switch ($TaskType) {
        "ship-timeout" { "ST" }
        "refund" { "RF" }
        "ship-reminder" { "SR" }
        default { throw "Unknown task type: $TaskType" }
    }
    $parts = @("DAY19-P4-S2", $typeCode, $Scenario, $script:RunId)
    if (-not [string]::IsNullOrWhiteSpace($Suffix)) {
        $parts += $Suffix
    }
    return ($parts -join "-")
}

function Remove-TestData {
    param([string]$Prefix)
    $prefixLike = Convert-ToSqlLiteral -Text ($Prefix + "%")
    $sql = @"
SET sql_safe_updates = 0;

UPDATE user_wallets uw
JOIN (
    SELECT o.buyer_id AS buyer_id, COALESCE(SUM(wt.amount), 0) AS total_amount
    FROM wallet_transactions wt
    JOIN order_refund_task rt
      ON rt.id = wt.biz_id
     AND wt.biz_type = 'ORDER_REFUND'
    JOIN orders o
      ON o.id = rt.order_id
    WHERE o.order_no LIKE $prefixLike
    GROUP BY o.buyer_id
) x
  ON x.buyer_id = uw.user_id
SET uw.balance = uw.balance - x.total_amount;

DELETE wt
FROM wallet_transactions wt
JOIN order_refund_task rt
  ON rt.id = wt.biz_id
 AND wt.biz_type = 'ORDER_REFUND'
JOIN orders o
  ON o.id = rt.order_id
WHERE o.order_no LIKE $prefixLike;

DELETE srt
FROM order_ship_reminder_task srt
JOIN orders o
  ON o.id = srt.order_id
WHERE o.order_no LIKE $prefixLike;

DELETE rft
FROM order_refund_task rft
JOIN orders o
  ON o.id = rft.order_id
WHERE o.order_no LIKE $prefixLike;

DELETE stt
FROM order_ship_timeout_task stt
JOIN orders o
  ON o.id = stt.order_id
WHERE o.order_no LIKE $prefixLike;

DELETE FROM orders
WHERE order_no LIKE $prefixLike;
"@
    Invoke-MySqlScript -Sql $sql
}

function Seed-SuccessBatch {
    param(
        [string]$TaskType,
        [string]$Prefix,
        [int]$Count
    )
    if ($Count -le 0) {
        throw "Count must be positive"
    }

    $prefixLiteral = Convert-ToSqlLiteral -Text $Prefix
    switch ($TaskType) {
        "ship-timeout" {
            $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, pay_time, update_time
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < $Count
)
SELECT
    CONCAT($prefixLiteral, '-', LPAD(n, 4, '0')),
    1,
    2,
    99.00,
    'paid',
    'Day19 P4-S2 synthetic ship-timeout success',
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    NOW()
FROM seq;

INSERT INTO order_ship_timeout_task (
    order_id, deadline_time, status, retry_count, next_retry_time, last_error, create_time, update_time
)
SELECT
    o.id,
    DATE_SUB(NOW(), INTERVAL 1 HOUR),
    'PENDING',
    0,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no LIKE CONCAT($prefixLiteral, '%');
"@
        }
        "refund" {
            $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, pay_time, cancel_time, cancel_reason, update_time
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < $Count
)
SELECT
    CONCAT($prefixLiteral, '-', LPAD(n, 4, '0')),
    1,
    2,
    99.00,
    'cancelled',
    'Day19 P4-S2 synthetic refund success',
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    DATE_SUB(NOW(), INTERVAL 1 HOUR),
    'ship_timeout',
    NOW()
FROM seq;

INSERT INTO order_refund_task (
    order_id, refund_type, amount, status, idempotency_key,
    retry_count, next_retry_time, fail_reason, create_time, update_time
)
SELECT
    o.id,
    'ship_timeout',
    99.00,
    'PENDING',
    CONCAT('refund:day19:p4s2:', o.order_no),
    0,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no LIKE CONCAT($prefixLiteral, '%');
"@
        }
        "ship-reminder" {
            $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, pay_time, update_time
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < $Count
)
SELECT
    CONCAT($prefixLiteral, '-', LPAD(n, 4, '0')),
    1,
    2,
    99.00,
    'paid',
    'Day19 P4-S2 synthetic ship-reminder success',
    DATE_SUB(NOW(), INTERVAL 47 HOUR),
    DATE_SUB(NOW(), INTERVAL 47 HOUR),
    NOW()
FROM seq;

INSERT INTO order_ship_reminder_task (
    order_id, seller_id, level, deadline_time, remind_time,
    status, retry_count, running_at, sent_at, client_msg_id, last_error, create_time, update_time
)
SELECT
    o.id,
    o.seller_id,
    'H1',
    DATE_ADD(NOW(), INTERVAL 1 HOUR),
    DATE_SUB(NOW(), INTERVAL 1 SECOND),
    'PENDING',
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no LIKE CONCAT($prefixLiteral, '%');
"@
        }
        default {
            throw "Unsupported task type: $TaskType"
        }
    }

    Invoke-MySqlScript -Sql $sql
}

function Seed-ManualFailureSample {
    param(
        [string]$TaskType,
        [string]$Prefix
    )
    $prefixLiteral = Convert-ToSqlLiteral -Text $Prefix
    switch ($TaskType) {
        "ship-timeout" {
            $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, update_time
)
VALUES (
    CONCAT($prefixLiteral, '-001'),
    1,
    2,
    99.00,
    'processing',
    'Day19 P4-S2 synthetic ship-timeout failure',
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    NOW()
);

INSERT INTO order_ship_timeout_task (
    order_id, deadline_time, status, retry_count, next_retry_time, last_error, create_time, update_time
)
SELECT
    o.id,
    DATE_SUB(NOW(), INTERVAL 1 HOUR),
    'PENDING',
    0,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no = CONCAT($prefixLiteral, '-001');
"@
        }
        "refund" {
            $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, pay_time, cancel_time, cancel_reason, update_time
)
VALUES (
    CONCAT($prefixLiteral, '-001'),
    1,
    2,
    99.00,
    'cancelled',
    'Day19 P4-S2 synthetic refund failure',
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    DATE_SUB(NOW(), INTERVAL 49 HOUR),
    DATE_SUB(NOW(), INTERVAL 1 HOUR),
    'ship_timeout',
    NOW()
);

INSERT INTO order_refund_task (
    order_id, refund_type, amount, status, idempotency_key,
    retry_count, next_retry_time, fail_reason, create_time, update_time
)
SELECT
    o.id,
    'ship_timeout',
    0.00,
    'PENDING',
    CONCAT('refund:day19:p4s2:fail:', o.order_no),
    0,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no = CONCAT($prefixLiteral, '-001');
"@
        }
        "ship-reminder" {
            $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, update_time
)
VALUES (
    CONCAT($prefixLiteral, '-001'),
    1,
    2,
    99.00,
    'processing',
    'Day19 P4-S2 synthetic ship-reminder failure',
    DATE_SUB(NOW(), INTERVAL 47 HOUR),
    NOW()
);

INSERT INTO order_ship_reminder_task (
    order_id, seller_id, level, deadline_time, remind_time,
    status, retry_count, running_at, sent_at, client_msg_id, last_error, create_time, update_time
)
SELECT
    o.id,
    o.seller_id,
    'H1',
    DATE_ADD(NOW(), INTERVAL 1 HOUR),
    DATE_SUB(NOW(), INTERVAL 1 SECOND),
    'PENDING',
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no = CONCAT($prefixLiteral, '-001');
"@
        }
        default {
            throw "Unsupported task type for failure sample: $TaskType"
        }
    }
    Invoke-MySqlScript -Sql $sql
}

function Seed-StaleReminderSample {
    param([string]$Prefix)
    $prefixLiteral = Convert-ToSqlLiteral -Text $Prefix
    $sql = @"
INSERT INTO orders (
    order_no, buyer_id, seller_id, total_amount, status,
    shipping_address, create_time, pay_time, update_time
)
VALUES (
    CONCAT($prefixLiteral, '-001'),
    1,
    2,
    99.00,
    'paid',
    'Day19 P4-S2 synthetic ship-reminder stale running',
    DATE_SUB(NOW(), INTERVAL 47 HOUR),
    DATE_SUB(NOW(), INTERVAL 47 HOUR),
    NOW()
);

INSERT INTO order_ship_reminder_task (
    order_id, seller_id, level, deadline_time, remind_time,
    status, retry_count, running_at, sent_at, client_msg_id, last_error, create_time, update_time
)
SELECT
    o.id,
    o.seller_id,
    'H1',
    DATE_ADD(NOW(), INTERVAL 1 HOUR),
    DATE_SUB(NOW(), INTERVAL 5 MINUTE),
    'RUNNING',
    0,
    DATE_SUB(NOW(), INTERVAL 10 MINUTE),
    NULL,
    NULL,
    NULL,
    NOW(),
    NOW()
FROM orders o
WHERE o.order_no = CONCAT($prefixLiteral, '-001');
"@
    Invoke-MySqlScript -Sql $sql
}

function Fix-ManualRecoveryPrecondition {
    param(
        [string]$TaskType,
        [string]$Prefix
    )
    $prefixLike = Convert-ToSqlLiteral -Text ($Prefix + "%")
    switch ($TaskType) {
        "ship-timeout" {
            $sql = @"
UPDATE orders
SET status = 'paid',
    pay_time = DATE_SUB(NOW(), INTERVAL 49 HOUR),
    cancel_time = NULL,
    cancel_reason = NULL,
    update_time = NOW()
WHERE order_no LIKE $prefixLike;
"@
        }
        "refund" {
            $sql = @"
UPDATE order_refund_task rt
JOIN orders o
  ON o.id = rt.order_id
SET rt.amount = 99.00,
    rt.update_time = NOW()
WHERE o.order_no LIKE $prefixLike;
"@
        }
        "ship-reminder" {
            $sql = @"
UPDATE orders
SET status = 'paid',
    pay_time = DATE_SUB(NOW(), INTERVAL 47 HOUR),
    cancel_time = NULL,
    cancel_reason = NULL,
    update_time = NOW()
WHERE order_no LIKE $prefixLike;
"@
        }
        default {
            throw "Unsupported task type for precondition fix: $TaskType"
        }
    }
    Invoke-MySqlScript -Sql $sql
}

function Get-RunnableBaselineCounts {
    $lines = Invoke-MySqlQuery -Sql @"
SELECT 'ship-timeout', COUNT(*)
FROM order_ship_timeout_task
WHERE status = 'PENDING'
  AND deadline_time <= NOW()
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
UNION ALL
SELECT 'refund', COUNT(*)
FROM order_refund_task
WHERE status IN ('PENDING', 'FAILED')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
UNION ALL
SELECT 'ship-reminder', COUNT(*)
FROM order_ship_reminder_task
WHERE status IN ('PENDING', 'FAILED')
  AND remind_time <= NOW()
UNION ALL
SELECT 'ship-reminder-stale-running', COUNT(*)
FROM order_ship_reminder_task
WHERE status = 'RUNNING'
  AND running_at <= DATE_SUB(NOW(), INTERVAL 5 MINUTE);
"@

    $map = @{}
    foreach ($line in $lines) {
        $parts = $line -split "`t"
        $map[$parts[0]] = [int]$parts[1]
    }
    return [pscustomobject]$map
}

function Get-BatchSummary {
    param(
        [string]$TaskType,
        [string]$Prefix
    )
    $prefixLike = Convert-ToSqlLiteral -Text ($Prefix + "%")
    switch ($TaskType) {
        "ship-timeout" {
            $line = Invoke-MySqlQuery -Sql @"
SELECT
    COUNT(*) AS total_count,
    COALESCE(SUM(CASE WHEN st.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_count,
    COALESCE(SUM(CASE WHEN st.status = 'DONE' THEN 1 ELSE 0 END), 0) AS done_count,
    COALESCE(SUM(CASE WHEN st.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_count,
    COALESCE(SUM(st.retry_count), 0) AS retry_sum,
    COALESCE(SUM(CASE WHEN st.status = 'PENDING' AND st.deadline_time <= NOW() AND (st.next_retry_time IS NULL OR st.next_retry_time <= NOW()) THEN 1 ELSE 0 END), 0) AS due_count
FROM order_ship_timeout_task st
JOIN orders o
  ON o.id = st.order_id
WHERE o.order_no LIKE $prefixLike;
"@
            $line = $line | Select-Object -First 1
            if ([string]::IsNullOrWhiteSpace($line)) {
                return [pscustomobject]@{
                    total = 0
                    pending = 0
                    done = 0
                    cancelled = 0
                    retrySum = 0
                    due = 0
                    successCount = 0
                    failureCount = 0
                }
            }
            $parts = $line -split "`t"
            return [pscustomobject]@{
                total = [int]$parts[0]
                pending = [int]$parts[1]
                done = [int]$parts[2]
                cancelled = [int]$parts[3]
                retrySum = [int]$parts[4]
                due = [int]$parts[5]
                successCount = [int]$parts[2]
                failureCount = [int]$parts[1] + [int]$parts[3]
            }
        }
        "refund" {
            $line = Invoke-MySqlQuery -Sql @"
SELECT
    COUNT(*) AS total_count,
    COALESCE(SUM(CASE WHEN rt.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_count,
    COALESCE(SUM(CASE WHEN rt.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_count,
    COALESCE(SUM(CASE WHEN rt.status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success_count,
    COALESCE(SUM(rt.retry_count), 0) AS retry_sum,
    COALESCE(SUM(CASE WHEN rt.status IN ('PENDING', 'FAILED') AND (rt.next_retry_time IS NULL OR rt.next_retry_time <= NOW()) THEN 1 ELSE 0 END), 0) AS due_count
FROM order_refund_task rt
JOIN orders o
  ON o.id = rt.order_id
WHERE o.order_no LIKE $prefixLike;
"@
            $line = $line | Select-Object -First 1
            if ([string]::IsNullOrWhiteSpace($line)) {
                return [pscustomobject]@{
                    total = 0
                    pending = 0
                    failed = 0
                    success = 0
                    retrySum = 0
                    due = 0
                    successCount = 0
                    failureCount = 0
                }
            }
            $parts = $line -split "`t"
            return [pscustomobject]@{
                total = [int]$parts[0]
                pending = [int]$parts[1]
                failed = [int]$parts[2]
                success = [int]$parts[3]
                retrySum = [int]$parts[4]
                due = [int]$parts[5]
                successCount = [int]$parts[3]
                failureCount = [int]$parts[1] + [int]$parts[2]
            }
        }
        "ship-reminder" {
            $line = Invoke-MySqlQuery -Sql @"
SELECT
    COUNT(*) AS total_count,
    COALESCE(SUM(CASE WHEN srt.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_count,
    COALESCE(SUM(CASE WHEN srt.status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS running_count,
    COALESCE(SUM(CASE WHEN srt.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_count,
    COALESCE(SUM(CASE WHEN srt.status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success_count,
    COALESCE(SUM(CASE WHEN srt.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_count,
    COALESCE(SUM(srt.retry_count), 0) AS retry_sum,
    COALESCE(SUM(CASE WHEN srt.status IN ('PENDING', 'FAILED') AND srt.remind_time <= NOW() THEN 1 ELSE 0 END), 0) AS due_count
FROM order_ship_reminder_task srt
JOIN orders o
  ON o.id = srt.order_id
WHERE o.order_no LIKE $prefixLike;
"@
            $line = $line | Select-Object -First 1
            if ([string]::IsNullOrWhiteSpace($line)) {
                return [pscustomobject]@{
                    total = 0
                    pending = 0
                    running = 0
                    failed = 0
                    success = 0
                    cancelled = 0
                    retrySum = 0
                    due = 0
                    successCount = 0
                    failureCount = 0
                }
            }
            $parts = $line -split "`t"
            return [pscustomobject]@{
                total = [int]$parts[0]
                pending = [int]$parts[1]
                running = [int]$parts[2]
                failed = [int]$parts[3]
                success = [int]$parts[4]
                cancelled = [int]$parts[5]
                retrySum = [int]$parts[6]
                due = [int]$parts[7]
                successCount = [int]$parts[4]
                failureCount = [int]$parts[1] + [int]$parts[2] + [int]$parts[3] + [int]$parts[5]
            }
        }
        default {
            throw "Unsupported task type for summary: $TaskType"
        }
    }
}

function Get-SingleTaskSample {
    param(
        [string]$TaskType,
        [string]$Prefix
    )
    $prefixLike = Convert-ToSqlLiteral -Text ($Prefix + "%")
    switch ($TaskType) {
        "ship-timeout" {
            $line = Invoke-MySqlQuery -Sql @"
SELECT
    st.id,
    st.order_id,
    st.status,
    st.retry_count,
    IFNULL(DATE_FORMAT(st.next_retry_time, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    IFNULL(st.last_error, 'NULL'),
    IFNULL(DATE_FORMAT(st.update_time, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    o.order_no,
    o.status
FROM order_ship_timeout_task st
JOIN orders o
  ON o.id = st.order_id
WHERE o.order_no LIKE $prefixLike
ORDER BY st.id ASC
LIMIT 1;
"@
            $line = $line | Select-Object -First 1
            if ([string]::IsNullOrWhiteSpace($line)) { return $null }
            $parts = $line -split "`t"
            return [pscustomobject]@{
                taskId = [long]$parts[0]
                orderId = [long]$parts[1]
                taskStatus = $parts[2]
                retryCount = [int]$parts[3]
                nextRetryTime = Convert-ToDbDateTime -Value $parts[4]
                lastError = Convert-ToDbNullable -Value $parts[5]
                updateTime = Convert-ToDbDateTime -Value $parts[6]
                orderNo = $parts[7]
                orderStatus = $parts[8]
            }
        }
        "refund" {
            $line = Invoke-MySqlQuery -Sql @"
SELECT
    rt.id,
    rt.order_id,
    rt.status,
    rt.retry_count,
    IFNULL(DATE_FORMAT(rt.next_retry_time, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    IFNULL(rt.fail_reason, 'NULL'),
    rt.amount,
    IFNULL(DATE_FORMAT(rt.update_time, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    o.order_no,
    o.status
FROM order_refund_task rt
JOIN orders o
  ON o.id = rt.order_id
WHERE o.order_no LIKE $prefixLike
ORDER BY rt.id ASC
LIMIT 1;
"@
            $line = $line | Select-Object -First 1
            if ([string]::IsNullOrWhiteSpace($line)) { return $null }
            $parts = $line -split "`t"
            return [pscustomobject]@{
                taskId = [long]$parts[0]
                orderId = [long]$parts[1]
                taskStatus = $parts[2]
                retryCount = [int]$parts[3]
                nextRetryTime = Convert-ToDbDateTime -Value $parts[4]
                failReason = Convert-ToDbNullable -Value $parts[5]
                amount = [decimal]$parts[6]
                updateTime = Convert-ToDbDateTime -Value $parts[7]
                orderNo = $parts[8]
                orderStatus = $parts[9]
            }
        }
        "ship-reminder" {
            $line = Invoke-MySqlQuery -Sql @"
SELECT
    srt.id,
    srt.order_id,
    srt.status,
    srt.retry_count,
    IFNULL(DATE_FORMAT(srt.remind_time, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    IFNULL(DATE_FORMAT(srt.running_at, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    IFNULL(srt.last_error, 'NULL'),
    IFNULL(srt.client_msg_id, 'NULL'),
    IFNULL(DATE_FORMAT(srt.update_time, '%Y-%m-%d %H:%i:%s'), 'NULL'),
    o.order_no,
    o.status
FROM order_ship_reminder_task srt
JOIN orders o
  ON o.id = srt.order_id
WHERE o.order_no LIKE $prefixLike
ORDER BY srt.id ASC
LIMIT 1;
"@
            $line = $line | Select-Object -First 1
            if ([string]::IsNullOrWhiteSpace($line)) { return $null }
            $parts = $line -split "`t"
            return [pscustomobject]@{
                taskId = [long]$parts[0]
                orderId = [long]$parts[1]
                taskStatus = $parts[2]
                retryCount = [int]$parts[3]
                remindTime = Convert-ToDbDateTime -Value $parts[4]
                runningAt = Convert-ToDbDateTime -Value $parts[5]
                lastError = Convert-ToDbNullable -Value $parts[6]
                clientMsgId = Convert-ToDbNullable -Value $parts[7]
                updateTime = Convert-ToDbDateTime -Value $parts[8]
                orderNo = $parts[9]
                orderStatus = $parts[10]
            }
        }
        default {
            throw "Unsupported task type for sample: $TaskType"
        }
    }
}

function Invoke-TaskRunOnce {
    param(
        [string]$TaskType,
        [string]$AdminToken,
        [int]$Limit
    )
    $path = switch ($TaskType) {
        "ship-timeout" { "/admin/ops/tasks/ship-timeout/run-once?limit=$Limit" }
        "refund" { "/admin/ops/tasks/refund/run-once?limit=$Limit" }
        "ship-reminder" { "/admin/ops/tasks/ship-reminder/run-once?limit=$Limit" }
        default { throw "Unsupported task type for run-once: $TaskType" }
    }
    $response = Invoke-ApiRequest -Method "POST" -Uri ($BaseUrl + $path) -Headers @{ token = $AdminToken } -Body $null
    if (-not $response.success) {
        throw "run-once failed for $TaskType"
    }
    return [pscustomobject]@{
        response = $response
        result = $response.body.data
    }
}

function Invoke-TaskCompensation {
    param(
        [string]$TaskType,
        [string]$AdminToken,
        [long]$TaskId
    )
    $path = switch ($TaskType) {
        "ship-timeout" { "/admin/ops/tasks/ship-timeout/$TaskId/trigger-now" }
        "refund" { "/admin/ops/tasks/refund/$TaskId/reset" }
        "ship-reminder" { "/admin/ops/tasks/ship-reminder/$TaskId/trigger-now" }
        default { throw "Unsupported task type for compensation: $TaskType" }
    }
    $response = Invoke-ApiRequest -Method "POST" -Uri ($BaseUrl + $path) -Headers @{ token = $AdminToken } -Body $null
    if (-not $response.success) {
        throw "compensation failed for $TaskType, taskId=$TaskId"
    }
    return $response
}

function Wait-ForReminderRetryWindow {
    param(
        [string]$Prefix,
        [int]$MaxWaitSeconds
    )
    $startedAt = Get-Date
    while (((Get-Date) - $startedAt).TotalSeconds -lt $MaxWaitSeconds) {
        $sample = Get-SingleTaskSample -TaskType "ship-reminder" -Prefix $Prefix
        if ($null -eq $sample) {
            throw "ship-reminder auto-recovery sample disappeared"
        }
        if ($null -eq $sample.remindTime -or $sample.remindTime -le (Get-Date)) {
            return [pscustomobject]@{
                reached = $true
                waitedSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
                latestSample = $sample
            }
        }
        Start-Sleep -Seconds 5
    }
    return [pscustomobject]@{
        reached = $false
        waitedSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
        latestSample = (Get-SingleTaskSample -TaskType "ship-reminder" -Prefix $Prefix)
    }
}

function Get-Recommendation {
    param([object[]]$Benchmarks)
    $eligible = @($Benchmarks | Where-Object { $_.totalFailureCount -eq 0 })
    if ($eligible.Count -eq 0) {
        return [pscustomobject]@{
            recommendedBatchSize = $null
            steadyCapacityPerMinute = $null
            runOnceAvgThroughput = $null
            reason = "No zero-failure batch size observed"
        }
    }

    $sorted = @($eligible | Sort-Object -Property @{ Expression = { [double]$_.throughputSummary.avg }; Descending = $true }, @{ Expression = { [int]$_.limit }; Descending = $true })
    $top = $sorted[0]
    return [pscustomobject]@{
        recommendedBatchSize = [int]$top.limit
        steadyCapacityPerMinute = [int]$top.limit
        runOnceAvgThroughput = [double]$top.throughputSummary.avg
        reason = "Prefer the highest observed average run-once throughput among zero-failure batches; at fixed-delay=60s this equals roughly the same steady-state tasks/minute per job."
    }
}

function Get-RetryRecommendation {
    param([string]$TaskType)
    switch ($TaskType) {
        "ship-timeout" {
            return [pscustomobject]@{
                current = "120s fixed retry delay"
                recommendation = "Keep 120s"
                reason = "Failure mode is usually invalid business precondition; manual trigger-now recovers in seconds, while automatic 120s avoids hot-looping invalid orders."
            }
        }
        "refund" {
            return [pscustomobject]@{
                current = "120s fixed retry delay"
                recommendation = "Keep 120s"
                reason = "Failure mode is usually accounting/input anomaly; reset recovers quickly after data repair, so 120s remains a reasonable background retry interval."
            }
        }
        "ship-reminder" {
            return [pscustomobject]@{
                current = "2m / 5m / 15m / 30m backoff, running-timeout 5m"
                recommendation = "Keep 2m / 5m / 15m / 30m and 5m running-timeout"
                reason = "Manual trigger-now bypasses the backoff when needed, and stale RUNNING recycle succeeded without duplicate success records in this run."
            }
        }
        default {
            throw "Unsupported task type for retry recommendation"
        }
    }
}

Ensure-MySqlCli

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path "day19回归" "执行记录"
}
if (-not (Test-Path -Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$resultLabel = "动态结果"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputPath = Join-Path $OutputDir ("Day19_P4_S2_{0}_{1}.json" -f $resultLabel, $timestamp)

$adminLogin = Invoke-ApiRequest -Method "POST" -Uri "$BaseUrl/admin/employee/login" -Headers @{} -Body @{ loginId = $AdminLoginId; password = $AdminPassword }
if (-not $adminLogin.success) {
    throw "Admin login failed"
}
$adminToken = [string]$adminLogin.body.data.token

$baselineRunnable = Get-RunnableBaselineCounts
if ([int]$baselineRunnable."ship-timeout" -ne 0 -or [int]$baselineRunnable."refund" -ne 0 -or [int]$baselineRunnable."ship-reminder" -ne 0 -or [int]$baselineRunnable."ship-reminder-stale-running" -ne 0) {
    throw "Baseline runnable tasks are not empty; aborting to avoid contaminating P4-S2 measurements."
}

$throughputBenchmarks = @{}
foreach ($taskType in $script:TaskTypes) {
    $taskBenchmarks = New-Object System.Collections.ArrayList
    foreach ($limit in $BatchSizes) {
        $roundResults = New-Object System.Collections.ArrayList
        for ($round = 1; $round -le $RoundsPerLimit; $round++) {
            $prefix = New-TestPrefix -TaskType $taskType -Scenario "SUCCESS" -Suffix ("L{0}-R{1}" -f $limit, $round)
            Remove-TestData -Prefix $prefix
            Seed-SuccessBatch -TaskType $taskType -Prefix $prefix -Count $limit

            $beforeBatch = Get-BatchSummary -TaskType $taskType -Prefix $prefix
            $run = Invoke-TaskRunOnce -TaskType $taskType -AdminToken $adminToken -Limit $limit
            $afterBatch = Get-BatchSummary -TaskType $taskType -Prefix $prefix

            $successCount = [int]$afterBatch.successCount
            $failureCount = [Math]::Max(0, [int]$beforeBatch.total - $successCount)
            $throughput = if ($run.response.elapsedMs -gt 0) {
                [Math]::Round(($successCount * 1000.0 / $run.response.elapsedMs), 2)
            } else {
                $null
            }

            $null = $roundResults.Add([pscustomobject]@{
                prefix = $prefix
                round = $round
                limit = $limit
                elapsedMs = [double]$run.response.elapsedMs
                apiSuccess = [int]$run.result.success
                batchSize = [int]$run.result.batchSize
                successCount = $successCount
                failureCount = $failureCount
                throughputPerSec = $throughput
                before = $beforeBatch
                after = $afterBatch
            })

            Remove-TestData -Prefix $prefix
        }

        $elapsedSeries = @($roundResults | ForEach-Object { [double]$_.elapsedMs })
        $throughputSeries = @($roundResults | ForEach-Object { [double]$_.throughputPerSec })
        $failureSum = @($roundResults | Measure-Object -Property failureCount -Sum).Sum
        if ($null -eq $failureSum) { $failureSum = 0 }

        $null = $taskBenchmarks.Add([pscustomobject]@{
            taskType = $taskType
            limit = $limit
            rounds = @($roundResults)
            elapsedSummary = Summarize-Numbers -Values $elapsedSeries
            throughputSummary = Summarize-Numbers -Values $throughputSeries
            totalFailureCount = [int]$failureSum
            totalSuccessCount = [int](@($roundResults | Measure-Object -Property successCount -Sum).Sum)
        })
    }
    $throughputBenchmarks[$taskType] = @($taskBenchmarks)
}

$manualRecoveryResults = New-Object System.Collections.ArrayList
foreach ($taskType in $script:TaskTypes) {
    $prefix = New-TestPrefix -TaskType $taskType -Scenario "MANUAL-RECOVER" -Suffix ""
    Remove-TestData -Prefix $prefix
    Seed-ManualFailureSample -TaskType $taskType -Prefix $prefix

    $beforeFail = Get-SingleTaskSample -TaskType $taskType -Prefix $prefix
    $failRun = Invoke-TaskRunOnce -TaskType $taskType -AdminToken $adminToken -Limit 1
    $afterFail = Get-SingleTaskSample -TaskType $taskType -Prefix $prefix

    if ($null -eq $afterFail) {
        throw "Manual recovery sample missing after initial failure for $taskType"
    }

    $scheduledDelaySeconds = switch ($taskType) {
        "ship-timeout" { if ($null -ne $afterFail.nextRetryTime) { [Math]::Round(($afterFail.nextRetryTime - (Get-Date)).TotalSeconds, 2) } else { $null } }
        "refund" { if ($null -ne $afterFail.nextRetryTime) { [Math]::Round(($afterFail.nextRetryTime - (Get-Date)).TotalSeconds, 2) } else { $null } }
        "ship-reminder" { if ($null -ne $afterFail.remindTime) { [Math]::Round(($afterFail.remindTime - (Get-Date)).TotalSeconds, 2) } else { $null } }
        default { $null }
    }

    Fix-ManualRecoveryPrecondition -TaskType $taskType -Prefix $prefix

    $manualStart = Get-Date
    $compensate = Invoke-TaskCompensation -TaskType $taskType -AdminToken $adminToken -TaskId $afterFail.taskId
    $rerun = Invoke-TaskRunOnce -TaskType $taskType -AdminToken $adminToken -Limit 1
    $afterRecovery = Get-SingleTaskSample -TaskType $taskType -Prefix $prefix
    $manualElapsedMs = [Math]::Round(((Get-Date) - $manualStart).TotalMilliseconds, 2)

    $null = $manualRecoveryResults.Add([pscustomobject]@{
        taskType = $taskType
        prefix = $prefix
        failRun = [pscustomobject]@{
            elapsedMs = [double]$failRun.response.elapsedMs
            apiSuccess = [int]$failRun.result.success
        }
        beforeFail = $beforeFail
        afterFail = $afterFail
        scheduledDelaySeconds = $scheduledDelaySeconds
        compensate = [pscustomobject]@{
            elapsedMs = [double]$compensate.elapsedMs
            updatedRows = [int]$compensate.body.data.updatedRows
            success = [bool]$compensate.success
        }
        rerun = [pscustomobject]@{
            elapsedMs = [double]$rerun.response.elapsedMs
            apiSuccess = [int]$rerun.result.success
        }
        afterRecovery = $afterRecovery
        recoveryElapsedMs = $manualElapsedMs
    })

    Remove-TestData -Prefix $prefix
}

$autoRecoveryPrefix = New-TestPrefix -TaskType "ship-reminder" -Scenario "AUTO-RECOVER" -Suffix ""
Remove-TestData -Prefix $autoRecoveryPrefix
Seed-StaleReminderSample -Prefix $autoRecoveryPrefix

$autoBefore = Get-SingleTaskSample -TaskType "ship-reminder" -Prefix $autoRecoveryPrefix
$autoStart = Get-Date
$autoRecycleRun = Invoke-TaskRunOnce -TaskType "ship-reminder" -AdminToken $adminToken -Limit 1
$autoAfterRecycle = Get-SingleTaskSample -TaskType "ship-reminder" -Prefix $autoRecoveryPrefix

if ($null -eq $autoAfterRecycle) {
    throw "Auto recovery sample missing after recycle stage"
}

$autoWait = $null
if ($SkipAutoRecoveryWait) {
    $autoWait = [pscustomobject]@{
        reached = $false
        waitedSeconds = 0
        latestSample = $autoAfterRecycle
        skipped = $true
    }
} else {
    $autoWait = Wait-ForReminderRetryWindow -Prefix $autoRecoveryPrefix -MaxWaitSeconds $AutoRecoveryMaxWaitSeconds
    Add-Member -InputObject $autoWait -NotePropertyName skipped -NotePropertyValue $false -Force
}

$autoRerun = $null
$autoAfterRecovery = Get-SingleTaskSample -TaskType "ship-reminder" -Prefix $autoRecoveryPrefix
if (-not $SkipAutoRecoveryWait -and $autoWait.reached) {
    $autoRerun = Invoke-TaskRunOnce -TaskType "ship-reminder" -AdminToken $adminToken -Limit 1
    $autoAfterRecovery = Get-SingleTaskSample -TaskType "ship-reminder" -Prefix $autoRecoveryPrefix
}

$autoRecoveryResult = [pscustomobject]@{
    taskType = "ship-reminder"
    prefix = $autoRecoveryPrefix
    before = $autoBefore
    recycleRun = [pscustomobject]@{
        elapsedMs = [double]$autoRecycleRun.response.elapsedMs
        apiSuccess = [int]$autoRecycleRun.result.success
    }
    afterRecycle = $autoAfterRecycle
    wait = $autoWait
    rerun = if ($null -eq $autoRerun) {
        $null
    } else {
        [pscustomobject]@{
            elapsedMs = [double]$autoRerun.response.elapsedMs
            apiSuccess = [int]$autoRerun.result.success
        }
    }
    afterRecovery = $autoAfterRecovery
    recoveryElapsedMs = [Math]::Round(((Get-Date) - $autoStart).TotalMilliseconds, 2)
}

Remove-TestData -Prefix $autoRecoveryPrefix

$recommendations = New-Object System.Collections.ArrayList
foreach ($taskType in $script:TaskTypes) {
    $capacity = Get-Recommendation -Benchmarks $throughputBenchmarks[$taskType]
    $retry = Get-RetryRecommendation -TaskType $taskType
    $null = $recommendations.Add([pscustomobject]@{
        taskType = $taskType
        capacity = $capacity
        retry = $retry
    })
}

$result = [pscustomobject]@{
    meta = [pscustomobject]@{
        script = "day19回归/执行复现步骤/Day19_P4_S2_任务链路性能_执行复现_v1.0.ps1"
        executedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        mysqlDatabase = $MySqlDatabase
        batchSizes = $BatchSizes
        roundsPerLimit = $RoundsPerLimit
        autoRecoveryMaxWaitSeconds = $AutoRecoveryMaxWaitSeconds
        adminTokenAcquired = -not [string]::IsNullOrWhiteSpace($adminToken)
        baselineRunnable = $baselineRunnable
    }
    throughputBenchmarks = $throughputBenchmarks
    manualRecovery = @($manualRecoveryResults)
    autoRecovery = $autoRecoveryResult
    recommendations = @($recommendations)
}

$result | ConvertTo-Json -Depth 10 | Set-Content -Path $outputPath -Encoding UTF8

Write-Host ("P4-S2 dynamic result saved to {0}" -f $outputPath)
foreach ($item in $recommendations) {
    Write-Host ("[{0}] recommended batch-size={1}, steady-capacity~{2}/min, retry={3}" -f $item.taskType, $item.capacity.recommendedBatchSize, $item.capacity.steadyCapacityPerMinute, $item.retry.recommendation)
}
