param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$LoginId = "13800000001",
    [string]$Password = "123456",
    [int]$ListPage = 1,
    [int]$ListPageSize = 20,
    [int]$ColdSamples = 5,
    [int]$WarmRounds = 3,
    [int]$WarmRequests = 180,
    [int]$Concurrency = 12,
    [int]$BurstRequests = 24,
    [int]$BurstConcurrency = 24,
    [int]$NullRequests = 60,
    [int]$NullConcurrency = 12,
    [int]$TimeoutSec = 15,
    [int]$JitterSamples = 8,
    [int]$LockObserveAttempts = 5,
    [long]$InvalidProductId = 999999999,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [int]$RedisDb = 2,
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-Percentile {
    param([double[]]$Values, [double]$Percent)
    if ($null -eq $Values -or $Values.Count -eq 0) { return $null }
    $sorted = $Values | Sort-Object
    $index = [int][Math]::Ceiling($Percent * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [Math]::Round([double]$sorted[$index], 2)
}

function Summarize-Run {
    param([object[]]$Rows, [datetime]$StartedAt, [datetime]$EndedAt)
    if ($null -eq $Rows) { $Rows = @() }
    $latencies = @($Rows | ForEach-Object { [double]$_.latencyMs })
    $total = $Rows.Count
    $success = @($Rows | Where-Object { $_.success -eq $true }).Count
    $failed = $total - $success
    $durationSec = [Math]::Max(0.001, ($EndedAt - $StartedAt).TotalSeconds)
    return [pscustomobject]@{
        totalRequests = $total
        successRequests = $success
        failedRequests = $failed
        errorRatePct = if ($total -eq 0) { 100.0 } else { [Math]::Round($failed * 100.0 / $total, 4) }
        throughputRps = [Math]::Round($total / $durationSec, 2)
        p50Ms = Get-Percentile -Values $latencies -Percent 0.50
        p95Ms = Get-Percentile -Values $latencies -Percent 0.95
        minMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Minimum).Minimum), 2) } else { $null }
        maxMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Maximum).Maximum), 2) } else { $null }
        avgMs = if ($latencies.Count -gt 0) { [Math]::Round(([double]($latencies | Measure-Object -Average).Average), 2) } else { $null }
        durationSec = [Math]::Round($durationSec, 3)
    }
}

function ConvertFrom-JsonSafe {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    try { return $Text | ConvertFrom-Json } catch { return $null }
}

function Invoke-SingleRequest {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [int[]]$ExpectedCodes,
        [string]$Label
    )

    $startedAt = Get-Date
    $httpStatus = $null
    $responseText = $null
    $errorType = $null
    $errorMessage = $null

    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec $TimeoutSec
        $httpStatus = [int]$resp.StatusCode
        $responseText = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $response = $_.Exception.Response
            try { $httpStatus = [int]$response.StatusCode } catch { $httpStatus = $null }
            try {
                $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
                $responseText = $reader.ReadToEnd()
                $reader.Dispose()
            } catch {
                $responseText = $null
            }
        }
        $errorType = "HTTP_EXCEPTION"
        $errorMessage = $_.Exception.Message
    }

    $endedAt = Get-Date
    $parsed = ConvertFrom-JsonSafe -Text $responseText
    $businessCode = if ($null -ne $parsed -and $null -ne $parsed.code) { [int]$parsed.code } else { $null }
    $success = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $ExpectedCodes -contains $businessCode)
    if (-not $success -and [string]::IsNullOrWhiteSpace($errorType)) {
        $errorType = "UNEXPECTED_RESPONSE"
        $errorMessage = if ($null -ne $parsed -and $null -ne $parsed.msg) { [string]$parsed.msg } else { $responseText }
    }

    return [pscustomobject]@{
        label = $Label
        httpStatus = $httpStatus
        businessCode = $businessCode
        success = $success
        latencyMs = [Math]::Round(($endedAt - $startedAt).TotalMilliseconds, 2)
        errorType = $errorType
        errorMessage = $errorMessage
        response = $parsed
        startedAt = $startedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
        endedAt = $endedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
    }
}

function Invoke-ParallelRequests {
    param(
        [string]$ScenarioName,
        [string]$Uri,
        [hashtable]$Headers,
        [int[]]$ExpectedCodes,
        [int]$TotalRequests,
        [int]$Concurrency
    )

    $perWorker = [int][Math]::Floor($TotalRequests / $Concurrency)
    $remainder = $TotalRequests % $Concurrency
    $jobs = @()
    $startedAt = Get-Date

    for ($worker = 0; $worker -lt $Concurrency; $worker++) {
        $workCount = $perWorker + $(if ($worker -lt $remainder) { 1 } else { 0 })
        if ($workCount -le 0) { continue }

        $jobs += Start-Job -ScriptBlock {
            param([string]$Uri, [hashtable]$Headers, [int[]]$ExpectedCodes, [int]$WorkCount, [int]$TimeoutSec, [string]$ScenarioName, [int]$WorkerId)

            $rows = New-Object System.Collections.Generic.List[object]
            for ($i = 0; $i -lt $WorkCount; $i++) {
                $reqStart = Get-Date
                $httpStatus = $null
                $responseText = $null
                $errorType = $null
                $errorMessage = $null
                try {
                    $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec $TimeoutSec
                    $httpStatus = [int]$resp.StatusCode
                    $responseText = $resp.Content
                } catch {
                    if ($_.Exception.Response) {
                        $response = $_.Exception.Response
                        try { $httpStatus = [int]$response.StatusCode } catch { $httpStatus = $null }
                        try {
                            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
                            $responseText = $reader.ReadToEnd()
                            $reader.Dispose()
                        } catch {
                            $responseText = $null
                        }
                    }
                    $errorType = "HTTP_EXCEPTION"
                    $errorMessage = $_.Exception.Message
                }
                $reqEnd = Get-Date
                $parsed = $null
                if (-not [string]::IsNullOrWhiteSpace($responseText)) {
                    try { $parsed = $responseText | ConvertFrom-Json } catch { $parsed = $null }
                }
                $businessCode = if ($null -ne $parsed -and $null -ne $parsed.code) { [int]$parsed.code } else { $null }
                $success = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $ExpectedCodes -contains $businessCode)
                if (-not $success -and [string]::IsNullOrWhiteSpace($errorType)) {
                    $errorType = "UNEXPECTED_RESPONSE"
                    $errorMessage = if ($null -ne $parsed -and $null -ne $parsed.msg) { [string]$parsed.msg } else { $responseText }
                }
                $rows.Add([pscustomobject]@{
                    scenario = $ScenarioName
                    workerId = $WorkerId
                    requestNo = $i + 1
                    httpStatus = $httpStatus
                    businessCode = $businessCode
                    success = $success
                    latencyMs = [Math]::Round(($reqEnd - $reqStart).TotalMilliseconds, 2)
                    errorType = $errorType
                    errorMessage = $errorMessage
                })
            }
            return $rows
        } -ArgumentList $Uri, $Headers, $ExpectedCodes, $workCount, $TimeoutSec, $ScenarioName, ($worker + 1)
    }

    Wait-Job -Job $jobs | Out-Null
    $rows = @($jobs | Receive-Job)
    $jobs | Remove-Job -Force | Out-Null
    $endedAt = Get-Date

    return [pscustomobject]@{
        rows = @($rows)
        summary = (Summarize-Run -Rows @($rows) -StartedAt $startedAt -EndedAt $endedAt)
        startedAt = $startedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
        endedAt = $endedAt.ToString("yyyy-MM-dd HH:mm:ss.fff")
    }
}

function Get-Sha256Hex {
    param([string]$Text)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hashBytes = $sha256.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Build-ListCacheKey {
    param([string]$Version, [int]$Page, [int]$PageSize)
    $signature = Get-Sha256Hex -Text "keyword=&category=&page=$Page&pageSize=$PageSize"
    return "cache:product:list:${Version}:${signature}:v1"
}

function Build-DetailCacheKey {
    param([long]$ProductId)
    return "cache:product:detail:${ProductId}:v1"
}

function Invoke-RedisRaw {
    param([string[]]$Arguments)
    return @(& $script:RedisCli @script:RedisArgs @Arguments 2>$null)
}

function Remove-RedisKey {
    param([string]$Key)
    if (-not [string]::IsNullOrWhiteSpace($Key)) {
        Invoke-RedisRaw -Arguments @("DEL", $Key) | Out-Null
    }
}

function Get-RedisValue {
    param([string]$Key)
    $raw = ((Invoke-RedisRaw -Arguments @("GET", $Key)) -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
    return $raw
}

function Get-RedisTtl {
    param([string]$Key)
    $raw = ((Invoke-RedisRaw -Arguments @("TTL", $Key)) -join "").Trim()
    $ttl = 0
    if ([int]::TryParse($raw, [ref]$ttl)) { return $ttl }
    return $null
}

function Test-RedisExists {
    param([string]$Key)
    return (((Invoke-RedisRaw -Arguments @("EXISTS", $Key)) -join "").Trim() -eq "1")
}

function Get-ListVersion {
    $version = Get-RedisValue -Key "cache:product:list:version"
    if ([string]::IsNullOrWhiteSpace($version)) { return "0" }
    return $version.Trim()
}

function Measure-ColdSamples {
    param([string]$Name, [string]$Uri, [hashtable]$Headers, [int[]]$ExpectedCodes, [string]$CacheKey, [string]$LockKey)
    $rows = New-Object System.Collections.Generic.List[object]
    $startedAt = Get-Date
    for ($i = 1; $i -le $ColdSamples; $i++) {
        Remove-RedisKey -Key $CacheKey
        Remove-RedisKey -Key $LockKey
        $result = Invoke-SingleRequest -Uri $Uri -Headers $Headers -ExpectedCodes $ExpectedCodes -Label "$Name-cold-$i"
        $rows.Add([pscustomobject]@{
            sample = $i
            httpStatus = $result.httpStatus
            businessCode = $result.businessCode
            success = $result.success
            latencyMs = $result.latencyMs
            ttlAfterRequestSec = (Get-RedisTtl -Key $CacheKey)
            errorType = $result.errorType
            errorMessage = $result.errorMessage
        })
    }
    $rowsArray = @($rows.ToArray())
    return [pscustomobject]@{
        samples = $rowsArray
        summary = (Summarize-Run -Rows $rowsArray -StartedAt $startedAt -EndedAt (Get-Date))
    }
}

function Measure-WarmScenario {
    param([string]$Name, [string]$Uri, [hashtable]$Headers, [int[]]$ExpectedCodes, [string]$CacheKey)
    $prime = Invoke-SingleRequest -Uri $Uri -Headers $Headers -ExpectedCodes $ExpectedCodes -Label "$Name-prime"
    $rounds = New-Object System.Collections.Generic.List[object]
    $allRows = New-Object System.Collections.Generic.List[object]
    $firstStart = $null
    $lastEnd = $null
    for ($round = 1; $round -le $WarmRounds; $round++) {
        $run = Invoke-ParallelRequests -ScenarioName "$Name-warm-$round" -Uri $Uri -Headers $Headers -ExpectedCodes $ExpectedCodes -TotalRequests $WarmRequests -Concurrency $Concurrency
        if ($null -eq $firstStart) { $firstStart = [datetime]$run.startedAt }
        $lastEnd = [datetime]$run.endedAt
        foreach ($row in $run.rows) { $allRows.Add($row) }
        $rounds.Add([pscustomobject]@{
            round = $round
            totalRequests = $run.summary.totalRequests
            successRequests = $run.summary.successRequests
            failedRequests = $run.summary.failedRequests
            errorRatePct = $run.summary.errorRatePct
            throughputRps = $run.summary.throughputRps
            p50Ms = $run.summary.p50Ms
            p95Ms = $run.summary.p95Ms
            avgMs = $run.summary.avgMs
        })
    }
    $roundsArray = @($rounds.ToArray())
    $allRowsArray = @($allRows.ToArray())
    return [pscustomobject]@{
        prime = [pscustomobject]@{
            latencyMs = $prime.latencyMs
            success = $prime.success
            ttlAfterPrimeSec = (Get-RedisTtl -Key $CacheKey)
        }
        rounds = $roundsArray
        summary = (Summarize-Run -Rows $allRowsArray -StartedAt $firstStart -EndedAt $lastEnd)
    }
}

function Measure-BurstScenario {
    param([string]$Name, [string]$Uri, [hashtable]$Headers, [int[]]$ExpectedCodes, [string]$CacheKey, [string]$LockKey, [double]$WarmP95Ms)
    Remove-RedisKey -Key $CacheKey
    Remove-RedisKey -Key $LockKey
    $run = Invoke-ParallelRequests -ScenarioName "$Name-burst" -Uri $Uri -Headers $Headers -ExpectedCodes $ExpectedCodes -TotalRequests $BurstRequests -Concurrency $BurstConcurrency
    $threshold = [Math]::Round([Math]::Max(($WarmP95Ms * 2.0), ($WarmP95Ms + 10.0)), 2)
    return [pscustomobject]@{
        thresholdMs = $threshold
        slowRequests = @($run.rows | Where-Object { $_.latencyMs -gt $threshold }).Count
        cacheExistsAfter = (Test-RedisExists -Key $CacheKey)
        lockExistsAfter = (Test-RedisExists -Key $LockKey)
        summary = $run.summary
    }
}

function Observe-Lock {
    param([string]$Uri, [hashtable]$Headers, [int[]]$ExpectedCodes, [string]$CacheKey, [string]$LockKey)
    $attempts = New-Object System.Collections.Generic.List[object]
    $observed = $false
    for ($i = 1; $i -le $LockObserveAttempts; $i++) {
        Remove-RedisKey -Key $CacheKey
        Remove-RedisKey -Key $LockKey
        $job = Start-Job -ScriptBlock {
            param([string]$Uri, [hashtable]$Headers, [int]$TimeoutSec, [int[]]$ExpectedCodes)
            $start = Get-Date
            $httpStatus = $null
            $responseText = $null
            try {
                $resp = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Get -Headers $Headers -TimeoutSec $TimeoutSec
                $httpStatus = [int]$resp.StatusCode
                $responseText = $resp.Content
            } catch {
                if ($_.Exception.Response) {
                    try { $httpStatus = [int]$_.Exception.Response.StatusCode } catch { $httpStatus = $null }
                }
            }
            $end = Get-Date
            $parsed = $null
            if (-not [string]::IsNullOrWhiteSpace($responseText)) {
                try { $parsed = $responseText | ConvertFrom-Json } catch { $parsed = $null }
            }
            $businessCode = if ($null -ne $parsed -and $null -ne $parsed.code) { [int]$parsed.code } else { $null }
            [pscustomobject]@{
                httpStatus = $httpStatus
                businessCode = $businessCode
                success = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $ExpectedCodes -contains $businessCode)
                latencyMs = [Math]::Round(($end - $start).TotalMilliseconds, 2)
            }
        } -ArgumentList $Uri, $Headers, $TimeoutSec, $ExpectedCodes

        $seen = $false
        $maxTtl = $null
        $observeStart = Get-Date
        while ($job.State -eq "Running" -and ((Get-Date) - $observeStart).TotalMilliseconds -lt 1500) {
            if (Test-RedisExists -Key $LockKey) {
                $seen = $true
                $ttl = Get-RedisTtl -Key $LockKey
                if ($null -ne $ttl -and ($null -eq $maxTtl -or $ttl -gt $maxTtl)) {
                    $maxTtl = $ttl
                }
            }
            Start-Sleep -Milliseconds 5
        }
        Wait-Job -Job $job | Out-Null
        $req = Receive-Job -Job $job
        Remove-Job -Job $job -Force | Out-Null
        $attempts.Add([pscustomobject]@{
            attempt = $i
            lockSeen = $seen
            maxObservedLockTtlSec = $maxTtl
            requestLatencyMs = $req.latencyMs
            success = $req.success
        })
        if ($seen) {
            $observed = $true
            break
        }
    }
    $attemptsArray = @($attempts.ToArray())
    return [pscustomobject]@{
        lockObserved = $observed
        attempts = $attemptsArray
    }
}

function Sample-TtlJitter {
    param([string]$Uri, [hashtable]$Headers, [int[]]$ExpectedCodes, [string]$CacheKey, [string]$LockKey)
    $rows = New-Object System.Collections.Generic.List[object]
    for ($i = 1; $i -le $JitterSamples; $i++) {
        Remove-RedisKey -Key $CacheKey
        Remove-RedisKey -Key $LockKey
        $req = Invoke-SingleRequest -Uri $Uri -Headers $Headers -ExpectedCodes $ExpectedCodes -Label "ttl-$i"
        $rows.Add([pscustomobject]@{
            sample = $i
            success = $req.success
            latencyMs = $req.latencyMs
            ttlSec = (Get-RedisTtl -Key $CacheKey)
        })
        Start-Sleep -Milliseconds 50
    }
    $rowsArray = @($rows.ToArray())
    $ttls = @($rowsArray | Where-Object { $null -ne $_.ttlSec } | ForEach-Object { [double]$_.ttlSec })
    return [pscustomobject]@{
        samples = $rowsArray
        summary = [pscustomobject]@{
            count = $rowsArray.Count
            minTtlSec = if ($ttls.Count -gt 0) { [int](($ttls | Measure-Object -Minimum).Minimum) } else { $null }
            maxTtlSec = if ($ttls.Count -gt 0) { [int](($ttls | Measure-Object -Maximum).Maximum) } else { $null }
            avgTtlSec = if ($ttls.Count -gt 0) { [Math]::Round(([double]($ttls | Measure-Object -Average).Average), 2) } else { $null }
        }
    }
}

function Test-SafeUnlock {
    param([string]$LockKey)
    $lua = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0"
    Remove-RedisKey -Key $LockKey
    Invoke-RedisRaw -Arguments @("SET", $LockKey, "token-a", "EX", "1", "NX") | Out-Null
    Start-Sleep -Seconds 2
    Invoke-RedisRaw -Arguments @("SET", $LockKey, "token-b", "EX", "10", "NX") | Out-Null
    $wrong = ((Invoke-RedisRaw -Arguments @("EVAL", $lua, "1", $LockKey, "token-a")) -join "").Trim()
    $valueAfterWrong = Get-RedisValue -Key $LockKey
    $ttlAfterWrong = Get-RedisTtl -Key $LockKey
    $correct = ((Invoke-RedisRaw -Arguments @("EVAL", $lua, "1", $LockKey, "token-b")) -join "").Trim()
    $valueAfterCorrect = Get-RedisValue -Key $LockKey
    return [pscustomobject]@{
        wrongTokenEvalResult = $wrong
        valueAfterWrongToken = $valueAfterWrong
        ttlAfterWrongTokenSec = $ttlAfterWrong
        correctTokenEvalResult = $correct
        valueAfterCorrectToken = $valueAfterCorrect
        wrongTokenDidNotDeleteNewLock = ($valueAfterWrong -eq "token-b")
        correctTokenDeletedLock = [string]::IsNullOrWhiteSpace($valueAfterCorrect)
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

$redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue
if ($null -eq $redisCli) {
    throw "redis-cli 未找到，无法执行缓存验证"
}
$script:RedisCli = $redisCli.Source
$script:RedisArgs = @("-h", $RedisHost, "-p", $RedisPort.ToString(), "-n", $RedisDb.ToString(), "--raw")

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$resultLabel = [string]([char]0x52A8) + [char]0x6001 + [char]0x9A8C + [char]0x8BC1 + [char]0x7ED3 + [char]0x679C
$outputPath = Join-Path $OutputDir ("Day19_P3_S2_{0}_{1}.json" -f $resultLabel, $timestamp)

$loginResp = Invoke-RestMethod -Uri "$BaseUrl/user/auth/login/password" -Method Post -ContentType "application/json" -Body (@{ loginId = $LoginId; password = $Password } | ConvertTo-Json)
if ($loginResp.code -ne 1 -or [string]::IsNullOrWhiteSpace($loginResp.data.token)) {
    throw "登录失败，无法执行 P3-S2"
}
$headers = @{ authentication = [string]$loginResp.data.token }

$listSeed = Invoke-SingleRequest -Uri "$BaseUrl/user/market/products?page=$ListPage&pageSize=$ListPageSize" -Headers $headers -ExpectedCodes @(1) -Label "seed-list"
if (-not $listSeed.success) {
    throw "市场列表预检失败"
}
$detailProductId = [long]$listSeed.response.data.list[0].productId
$detailTitle = [string]$listSeed.response.data.list[0].title
$listVersion = Get-ListVersion
$listCacheKey = Build-ListCacheKey -Version $listVersion -Page $ListPage -PageSize $ListPageSize
$detailCacheKey = Build-DetailCacheKey -ProductId $detailProductId
$invalidCacheKey = Build-DetailCacheKey -ProductId $InvalidProductId
$listLockKey = "${listCacheKey}:lock"
$detailLockKey = "${detailCacheKey}:lock"
$invalidLockKey = "${invalidCacheKey}:lock"
$listUri = "$BaseUrl/user/market/products?page=$ListPage&pageSize=$ListPageSize"
$detailUri = "$BaseUrl/user/market/products/$detailProductId"
$invalidUri = "$BaseUrl/user/market/products/$InvalidProductId"

$listCold = Measure-ColdSamples -Name "market_product_list" -Uri $listUri -Headers $headers -ExpectedCodes @(1) -CacheKey $listCacheKey -LockKey $listLockKey
$listWarm = Measure-WarmScenario -Name "market_product_list" -Uri $listUri -Headers $headers -ExpectedCodes @(1) -CacheKey $listCacheKey
$detailCold = Measure-ColdSamples -Name "market_product_detail" -Uri $detailUri -Headers $headers -ExpectedCodes @(1) -CacheKey $detailCacheKey -LockKey $detailLockKey
$detailWarm = Measure-WarmScenario -Name "market_product_detail" -Uri $detailUri -Headers $headers -ExpectedCodes @(1) -CacheKey $detailCacheKey

Remove-RedisKey -Key $invalidCacheKey
Remove-RedisKey -Key $invalidLockKey
$nullCold = Invoke-SingleRequest -Uri $invalidUri -Headers $headers -ExpectedCodes @(0) -Label "null-cold"
$nullMarker = Get-RedisValue -Key $invalidCacheKey
$nullTtl = Get-RedisTtl -Key $invalidCacheKey
$nullWarm = Invoke-ParallelRequests -ScenarioName "market_product_detail_null" -Uri $invalidUri -Headers $headers -ExpectedCodes @(0) -TotalRequests $NullRequests -Concurrency $NullConcurrency

$listBurst = Measure-BurstScenario -Name "market_product_list" -Uri $listUri -Headers $headers -ExpectedCodes @(1) -CacheKey $listCacheKey -LockKey $listLockKey -WarmP95Ms ([double]$listWarm.summary.p95Ms)
$detailBurst = Measure-BurstScenario -Name "market_product_detail" -Uri $detailUri -Headers $headers -ExpectedCodes @(1) -CacheKey $detailCacheKey -LockKey $detailLockKey -WarmP95Ms ([double]$detailWarm.summary.p95Ms)
$listLockObs = Observe-Lock -Uri $listUri -Headers $headers -ExpectedCodes @(1) -CacheKey $listCacheKey -LockKey $listLockKey
$detailLockObs = Observe-Lock -Uri $detailUri -Headers $headers -ExpectedCodes @(1) -CacheKey $detailCacheKey -LockKey $detailLockKey
$detailJitter = Sample-TtlJitter -Uri $detailUri -Headers $headers -ExpectedCodes @(1) -CacheKey $detailCacheKey -LockKey $detailLockKey
$listJitter = Sample-TtlJitter -Uri $listUri -Headers $headers -ExpectedCodes @(1) -CacheKey $listCacheKey -LockKey $listLockKey
$safeUnlock = Test-SafeUnlock -LockKey "cache:product:detail:p3s2-safe-unlock-check:lock"

$result = [pscustomobject]@{
    meta = [pscustomobject]@{
        runAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        script = $PSCommandPath
    }
    config = [pscustomobject]@{
        coldSamples = $ColdSamples
        warmRounds = $WarmRounds
        warmRequests = $WarmRequests
        concurrency = $Concurrency
        burstRequests = $BurstRequests
        burstConcurrency = $BurstConcurrency
        nullRequests = $NullRequests
        nullConcurrency = $NullConcurrency
        jitterSamples = $JitterSamples
        invalidProductId = $InvalidProductId
    }
    preflight = [pscustomobject]@{
        tokenAcquired = $true
        onSaleTotal = [int]$listSeed.response.data.total
        detailProductId = $detailProductId
        detailTitle = $detailTitle
        listVersion = $listVersion
        listCacheKey = $listCacheKey
        detailCacheKey = $detailCacheKey
        invalidCacheKey = $invalidCacheKey
    }
    scenarios = @(
        [pscustomobject]@{
            name = "market_product_list"
            endpoint = "GET /user/market/products?page=$ListPage&pageSize=$ListPageSize"
            cold = $listCold
            warm = $listWarm
            burst = $listBurst
        },
        [pscustomobject]@{
            name = "market_product_detail"
            endpoint = "GET /user/market/products/$detailProductId"
            cold = $detailCold
            warm = $detailWarm
            burst = $detailBurst
        }
    )
    nullCache = [pscustomobject]@{
        firstRequestLatencyMs = $nullCold.latencyMs
        firstRequestCode = $nullCold.businessCode
        markerValue = $nullMarker
        ttlAfterFirstRequestSec = $nullTtl
        repeatedSummary = $nullWarm.summary
    }
    lockObservation = [pscustomobject]@{
        list = $listLockObs
        detail = $detailLockObs
    }
    ttlJitter = [pscustomobject]@{
        detail = $detailJitter
        list = $listJitter
    }
    safeUnlock = $safeUnlock
    conclusions = [pscustomobject]@{
        listColdVsWarmP50DeltaMs = [Math]::Round(([double]$listCold.summary.p50Ms - [double]$listWarm.summary.p50Ms), 2)
        listColdVsWarmP50GainPct = if ([double]$listCold.summary.p50Ms -gt 0) { [Math]::Round((([double]$listCold.summary.p50Ms - [double]$listWarm.summary.p50Ms) * 100.0 / [double]$listCold.summary.p50Ms), 2) } else { $null }
        detailColdVsWarmP50DeltaMs = [Math]::Round(([double]$detailCold.summary.p50Ms - [double]$detailWarm.summary.p50Ms), 2)
        detailColdVsWarmP50GainPct = if ([double]$detailCold.summary.p50Ms -gt 0) { [Math]::Round((([double]$detailCold.summary.p50Ms - [double]$detailWarm.summary.p50Ms) * 100.0 / [double]$detailCold.summary.p50Ms), 2) } else { $null }
        nullCacheMarkerOk = ($nullMarker -eq "__NULL__")
        listLockObserved = $listLockObs.lockObserved
        detailLockObserved = $detailLockObs.lockObserved
        safeUnlockOk = ($safeUnlock.wrongTokenDidNotDeleteNewLock -and $safeUnlock.correctTokenDeletedLock)
    }
}

[System.IO.File]::WriteAllText($outputPath, ($result | ConvertTo-Json -Depth 10), [System.Text.Encoding]::UTF8)
Write-Host "P3-S2 result: $outputPath"
Write-Host ("List cold p50={0}ms | warm p50={1}ms" -f $listCold.summary.p50Ms, $listWarm.summary.p50Ms)
Write-Host ("Detail cold p50={0}ms | warm p50={1}ms" -f $detailCold.summary.p50Ms, $detailWarm.summary.p50Ms)
Write-Host ("Null marker={0} | TTL={1}" -f $nullMarker, $nullTtl)
