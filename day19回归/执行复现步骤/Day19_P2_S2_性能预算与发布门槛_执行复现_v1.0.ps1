param(
    # 服务地址。仅在触发 P2-S1 回归时传递给回归脚本。
    [string]$BaseUrl = "http://localhost:8080",

    # 执行记录输出目录。P2-S2 动态证据会写到该目录。
    [string]$OutputDir = "day19回归/执行记录",

    # 固定基线文件（冻结对照）。默认指向 Day19 已冻结的 P2-S1 基线结果。
    [string]$BaselineFile = "day19回归/执行记录/Day19_P2_S1_动态结果_2026-03-06_09-54-21.json",

    # 候选文件（当前待评估版本）。留空时自动取 OutputDir 下最新的 P2-S1 结果。
    [string]$CandidateFile = "",

    # 是否先跑一次 P2-S1 回归，再用新结果做 P2-S2 门槛判定。
    [switch]$RunRegression,

    # P2-S1 回归脚本路径。仅在 -RunRegression 时使用。
    [string]$RegressionScript = "day19回归/执行复现步骤/Day19_P2_S1_核心接口性能基线_执行复现_v1.0.ps1",

    # 透传给 P2-S1 回归脚本的核心参数，用于保证口径一致和可复现。
    [int]$Rounds = 3,
    [int]$WarmupRequests = 60,
    [int]$MeasuredRequests = 300,
    [int]$Concurrency = 12,
    [int]$TimeoutSec = 15
)

<#
脚本定位（Day19 / P2-S2）：
1) 读取 P2-S1 基线与当前候选结果；
2) 对每条核心链路执行“绝对预算 + 相对退化”双重判定；
3) 输出发布结论（PASS / MINOR / MAJOR / BLOCKER）与回滚建议；
4) 生成结构化 JSON 证据，供发布审批、回归审计和文档回填。

说明：
- P2-S1 负责“测量数据”，P2-S2 负责“发布判定”。
- 本脚本不引入新平台，完全复用现有 Day19 回归脚本与 JSON 结构。
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Stage {
    param([string]$Message)
    Write-Host ("`n[Day19-P2-S2] {0}" -f $Message) -ForegroundColor Cyan
}

function Assert-PathExists {
    param(
        [string]$Path,
        [string]$Label
    )

    if (-not (Test-Path -Path $Path)) {
        throw "$Label not found: $Path"
    }
}

function Get-Scenario {
    param(
        [object]$ResultJson,
        [string]$ScenarioName
    )

    # 统一通过 name 精确匹配，确保预算与链路一一对应。
    return @(
        $ResultJson.scenarios |
            Where-Object { $_.name -eq $ScenarioName } |
            Select-Object -First 1
    )[0]
}

function Round2 {
    param([double]$Value)
    return [Math]::Round($Value, 2)
}

function Get-DeltaPct {
    param(
        [double]$Baseline,
        [double]$Current
    )

    # 基线为 0 时无法计算比例，返回 $null 交给上层按“不可计算”处理。
    if ($Baseline -eq 0) {
        return $null
    }
    return Round2 ((($Current - $Baseline) * 100.0) / $Baseline)
}

function Get-DropPct {
    param(
        [double]$Baseline,
        [double]$Current
    )

    if ($Baseline -eq 0) {
        return $null
    }
    return Round2 ((($Baseline - $Current) * 100.0) / $Baseline)
}

function Get-OverallDecision {
    param([object[]]$ScenarioDecisions)

    $levels = @($ScenarioDecisions | ForEach-Object { $_.decisionLevel })
    if ($levels -contains "BLOCKER") { return "BLOCKER" }
    if ($levels -contains "MAJOR") { return "MAJOR" }
    if ($levels -contains "MINOR") { return "MINOR" }
    return "PASS"
}

function Get-RollbackAdvice {
    param([string]$OverallDecision)

    switch ($OverallDecision) {
        "BLOCKER" {
            return [pscustomobject]@{
                releaseAction = "BLOCK_RELEASE"
                advice = "Immediately rollback to last stable version and stop release."
                requiredActions = @(
                    "Freeze deployment window",
                    "Collect performance and error evidence",
                    "Open P0 incident and assign owners",
                    "Re-run full P2-S1 regression after fix"
                )
            }
        }
        "MAJOR" {
            return [pscustomobject]@{
                releaseAction = "HOLD_RELEASE"
                advice = "Do not release now. Fix performance regression and rerun."
                requiredActions = @(
                    "Identify hotspot chain",
                    "Apply optimization patch",
                    "Run one full P2-S1 regression",
                    "Re-evaluate with this P2-S2 script"
                )
            }
        }
        "MINOR" {
            return [pscustomobject]@{
                releaseAction = "RELEASE_WITH_RISK"
                advice = "Release is allowed with risk note and enhanced monitoring."
                requiredActions = @(
                    "Record risk in release note",
                    "Set temporary watch on affected APIs",
                    "Plan fix in next iteration"
                )
            }
        }
        default {
            return [pscustomobject]@{
                releaseAction = "RELEASE_PASS"
                advice = "All gates passed. Release can proceed."
                requiredActions = @(
                    "Archive JSON evidence",
                    "Attach gate result to release ticket"
                )
            }
        }
    }
}

function Get-LatestP2S1File {
    param([string]$Dir)

    return Get-ChildItem -Path $Dir -File -Filter "Day19_P2_S1_动态结果_*.json" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

Write-Stage "Prepare output path"
if (-not (Test-Path -Path $OutputDir)) {
    New-Item -Path $OutputDir -ItemType Directory -Force | Out-Null
}
$runAt = Get-Date
$timestamp = $runAt.ToString("yyyy-MM-dd_HH-mm-ss")
$outputPath = Join-Path $OutputDir ("Day19_P2_S2_动态结果_{0}.json" -f $timestamp)

Write-Stage "Resolve baseline and candidate files"
Assert-PathExists -Path $BaselineFile -Label "BaselineFile"

if ($RunRegression) {
    Write-Stage "Run P2-S1 regression script first"
    Assert-PathExists -Path $RegressionScript -Label "RegressionScript"

    $beforeLatest = Get-LatestP2S1File -Dir $OutputDir
    $beforeLatestPath = if ($null -eq $beforeLatest) { "" } else { $beforeLatest.FullName }

    # 用显式参数透传，避免隐式依赖，保证回归口径透明。
    & powershell -NoProfile -ExecutionPolicy Bypass -File $RegressionScript `
        -BaseUrl $BaseUrl `
        -Rounds $Rounds `
        -WarmupRequests $WarmupRequests `
        -MeasuredRequests $MeasuredRequests `
        -Concurrency $Concurrency `
        -TimeoutSec $TimeoutSec `
        -OutputDir $OutputDir

    if ($LASTEXITCODE -ne 0) {
        throw "P2-S1 regression script failed. ExitCode=$LASTEXITCODE"
    }

    $afterLatest = Get-LatestP2S1File -Dir $OutputDir
    if ($null -eq $afterLatest) {
        throw "P2-S1 regression finished but no Day19_P2_S1_动态结果_*.json found."
    }

    $afterLatestPath = $afterLatest.FullName
    if (($beforeLatestPath -ne "") -and ($afterLatestPath -eq $beforeLatestPath)) {
        Write-Host "Warning: latest P2-S1 file path unchanged, still using latest file as candidate." -ForegroundColor Yellow
    }

    $CandidateFile = $afterLatestPath
}

if ([string]::IsNullOrWhiteSpace($CandidateFile)) {
    # 未显式指定候选文件时，默认取最新 P2-S1 动态结果。
    $latest = Get-LatestP2S1File -Dir $OutputDir
    if ($null -eq $latest) {
        throw "No candidate P2-S1 result found. Please pass -CandidateFile or use -RunRegression."
    }
    $CandidateFile = $latest.FullName
}
Assert-PathExists -Path $CandidateFile -Label "CandidateFile"

Write-Stage "Load baseline and candidate JSON"
$baselineJson = Get-Content -Path $BaselineFile -Encoding UTF8 | ConvertFrom-Json
$candidateJson = Get-Content -Path $CandidateFile -Encoding UTF8 | ConvertFrom-Json

# 预算定义：
# - abs* 代表绝对门槛（硬门槛）
# - rel* 代表相对退化门槛（对比基线）
$budgetList = @(
    [pscustomobject]@{
        scenario = "login_password"
        endpoint = "POST /user/auth/login/password"
        absP95Ms = 500.00
        absErrorRatePct = 1.00
        absThroughputRps = 30.00
        minSamples = 600
        relP95IncreasePct = 20.00
        relThroughputDropPct = 20.00
        relErrorRateIncreasePctPoint = 0.50
    },
    [pscustomobject]@{
        scenario = "market_product_list"
        endpoint = "GET /user/market/products?page=1&pageSize=20"
        absP95Ms = 80.00
        absErrorRatePct = 1.00
        absThroughputRps = 40.00
        minSamples = 600
        relP95IncreasePct = 20.00
        relThroughputDropPct = 20.00
        relErrorRateIncreasePctPoint = 0.50
    },
    [pscustomobject]@{
        scenario = "payment_callback"
        endpoint = "POST /payment/callback"
        absP95Ms = 120.00
        absErrorRatePct = 1.00
        absThroughputRps = 35.00
        minSamples = 600
        relP95IncreasePct = 20.00
        relThroughputDropPct = 20.00
        relErrorRateIncreasePctPoint = 0.50
    }
)

Write-Stage "Evaluate budgets and degradation"
$scenarioResults = New-Object System.Collections.Generic.List[object]

foreach ($budget in $budgetList) {
    $baselineScenario = Get-Scenario -ResultJson $baselineJson -ScenarioName $budget.scenario
    $currentScenario = Get-Scenario -ResultJson $candidateJson -ScenarioName $budget.scenario

    if ($null -eq $baselineScenario) { throw "Scenario missing in baseline file: $($budget.scenario)" }
    if ($null -eq $currentScenario) { throw "Scenario missing in candidate file: $($budget.scenario)" }

    # 固定取 scenario.baseline 汇总字段，避免 round 级别波动影响发布判断。
    $baseP95 = [double]$baselineScenario.baseline.p95Ms
    $baseErr = [double]$baselineScenario.baseline.errorRatePct
    $baseRps = [double]$baselineScenario.baseline.throughputRps
    $baseTotal = [int]$baselineScenario.baseline.totalRequests

    $currP95 = [double]$currentScenario.baseline.p95Ms
    $currErr = [double]$currentScenario.baseline.errorRatePct
    $currRps = [double]$currentScenario.baseline.throughputRps
    $currTotal = [int]$currentScenario.baseline.totalRequests

    # 绝对预算判定（硬门槛）。
    $passAbsP95 = ($currP95 -le $budget.absP95Ms)
    $passAbsErr = ($currErr -le $budget.absErrorRatePct)
    $passAbsRps = ($currRps -ge $budget.absThroughputRps)
    $passAbsSample = ($currTotal -ge $budget.minSamples)

    # 相对退化判定（对比基线）。
    $actualP95IncreasePct = Get-DeltaPct -Baseline $baseP95 -Current $currP95
    $actualRpsDropPct = Get-DropPct -Baseline $baseRps -Current $currRps
    $actualErrIncreasePctPoint = Round2 ($currErr - $baseErr)

    $passRelP95 = ($null -eq $actualP95IncreasePct) -or ($actualP95IncreasePct -le $budget.relP95IncreasePct)
    $passRelRps = ($null -eq $actualRpsDropPct) -or ($actualRpsDropPct -le $budget.relThroughputDropPct)
    $passRelErr = ($actualErrIncreasePctPoint -le $budget.relErrorRateIncreasePctPoint)

    $hasHardFail = (-not $passAbsP95) -or (-not $passAbsErr) -or (-not $passAbsRps) -or (-not $passAbsSample)
    $hasRelFail = (-not $passRelP95) -or (-not $passRelRps) -or (-not $passRelErr)

    # 发布级别判定：
    # BLOCKER > MAJOR > MINOR > PASS
    # BLOCKER 条件比 MAJOR 更严格，用于快速触发阻断/回滚建议。
    $decision = "PASS"
    if ($hasHardFail) {
        $isBlocker = $false
        if ($currErr -gt ($budget.absErrorRatePct + 1.00)) { $isBlocker = $true }
        if ($currP95 -gt ($budget.absP95Ms * 1.20)) { $isBlocker = $true }
        if ($currRps -lt ($budget.absThroughputRps * 0.70)) { $isBlocker = $true }
        if (-not $passAbsSample) { $isBlocker = $true }

        $decision = if ($isBlocker) { "BLOCKER" } else { "MAJOR" }
    }
    elseif ($hasRelFail) {
        $decision = "MINOR"
    }

    $scenarioResults.Add([pscustomobject]@{
        scenario = $budget.scenario
        endpoint = $budget.endpoint
        decisionLevel = $decision
        baseline = [pscustomobject]@{
            p95Ms = $baseP95
            errorRatePct = $baseErr
            throughputRps = $baseRps
            totalRequests = $baseTotal
        }
        current = [pscustomobject]@{
            p95Ms = $currP95
            errorRatePct = $currErr
            throughputRps = $currRps
            totalRequests = $currTotal
        }
        absoluteBudget = [pscustomobject]@{
            p95Ms = $budget.absP95Ms
            errorRatePct = $budget.absErrorRatePct
            throughputRps = $budget.absThroughputRps
            minSamples = $budget.minSamples
        }
        absoluteCheck = [pscustomobject]@{
            p95Pass = $passAbsP95
            errorRatePass = $passAbsErr
            throughputPass = $passAbsRps
            samplePass = $passAbsSample
        }
        relativeBudget = [pscustomobject]@{
            p95IncreasePct = $budget.relP95IncreasePct
            throughputDropPct = $budget.relThroughputDropPct
            errorRateIncreasePctPoint = $budget.relErrorRateIncreasePctPoint
        }
        relativeActual = [pscustomobject]@{
            p95IncreasePct = $actualP95IncreasePct
            throughputDropPct = $actualRpsDropPct
            errorRateIncreasePctPoint = $actualErrIncreasePctPoint
        }
        relativeCheck = [pscustomobject]@{
            p95Pass = $passRelP95
            throughputPass = $passRelRps
            errorRatePass = $passRelErr
        }
    })
}

$overallDecision = Get-OverallDecision -ScenarioDecisions $scenarioResults
$rollbackAdvice = Get-RollbackAdvice -OverallDecision $overallDecision

Write-Stage "Build release checklist result"
$checklist = [ordered]@{
    check_01_regression_executed_or_candidate_present = $true
    check_02_rounds_ge_3 = ([int]$candidateJson.config.rounds -ge 3)
    check_03_each_scenario_sample_ge_600 = (@($scenarioResults | Where-Object { $_.absoluteCheck.samplePass -eq $false }).Count -eq 0)
    check_04_each_scenario_error_le_1pct = (@($scenarioResults | Where-Object { $_.absoluteCheck.errorRatePass -eq $false }).Count -eq 0)
    check_05_each_scenario_p95_within_budget = (@($scenarioResults | Where-Object { $_.absoluteCheck.p95Pass -eq $false }).Count -eq 0)
    check_06_each_scenario_rps_within_budget = (@($scenarioResults | Where-Object { $_.absoluteCheck.throughputPass -eq $false }).Count -eq 0)
    check_07_relative_degradation_evaluated = $true
    check_08_release_decision_generated = (-not [string]::IsNullOrWhiteSpace($overallDecision))
}

Write-Stage "Write JSON evidence"
$evidence = [ordered]@{
    meta = [ordered]@{
        runAt = $runAt.ToString("yyyy-MM-dd HH:mm:ss")
        baseUrl = $BaseUrl
        outputPath = $outputPath
        runRegression = [bool]$RunRegression
        script = "day19回归/执行复现步骤/Day19_P2_S2_性能预算与发布门槛_执行复现_v1.0.ps1"
    }
    inputs = [ordered]@{
        baselineFile = $BaselineFile
        candidateFile = $CandidateFile
        regressionScript = $RegressionScript
        regressionArgs = [ordered]@{
            rounds = $Rounds
            warmupRequests = $WarmupRequests
            measuredRequests = $MeasuredRequests
            concurrency = $Concurrency
            timeoutSec = $TimeoutSec
        }
    }
    budgets = $budgetList
    scenarioResults = $scenarioResults
    overallDecision = $overallDecision
    rollbackAdvice = $rollbackAdvice
    checklist = $checklist
}

($evidence | ConvertTo-Json -Depth 40) | Out-File -FilePath $outputPath -Encoding UTF8

Write-Host "Result file:" -ForegroundColor Green
Write-Host $outputPath
Write-Host ""
Write-Host "Overall decision:" -ForegroundColor Green
Write-Host $overallDecision
Write-Host ""
Write-Host "Rollback action:" -ForegroundColor Green
Write-Host $rollbackAdvice.releaseAction
Write-Host ""
Write-Host "Scenario decisions:" -ForegroundColor Green
foreach ($item in $scenarioResults) {
    Write-Host ("- {0}: {1}" -f $item.scenario, $item.decisionLevel)
}
