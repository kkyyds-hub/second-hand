param(
    [string[]]$ScanPaths = @(".")
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command rg -ErrorAction SilentlyContinue)) {
    Write-Host "[secret-scan] rg not found, skip scan."
    exit 0
}

$patterns = @(
    @{ Name = "JWT"; Regex = 'eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}' },
    @{ Name = "BearerToken"; Regex = 'Bearer\s+(?!<redacted_token>)[A-Za-z0-9._-]{20,}' },
    @{ Name = "PasswordJson"; Regex = '"password"\s*:\s*"(?!\{\{)(?!<redacted_password>).*?"' },
    @{ Name = "PasswordInline"; Regex = 'password=(?!<redacted_password>)(?!\{\{)[^\s,;|]+' }
)

$commonArgs = @(
    "-n",
    "--pcre2",
    "--glob", "!**/.git/**",
    "--glob", "!**/node_modules/**",
    "--glob", "!**/target/**",
    "--glob", "!**/*.html",
    "--glob", "day18*/**"
)

$findings = @()

foreach ($pattern in $patterns) {
    $args = @($commonArgs + @($pattern.Regex) + $ScanPaths)
    $output = & rg @args
    if ($LASTEXITCODE -gt 1) {
        throw "[secret-scan] rg failed for pattern: $($pattern.Name)"
    }
    if ($LASTEXITCODE -eq 0 -and $output) {
        foreach ($line in $output) {
            $findings += "[{0}] {1}" -f $pattern.Name, $line
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Host "[secret-scan] Found sensitive-like literals:"
    $findings | ForEach-Object { Write-Host $_ }
    exit 1
}

Write-Host "[secret-scan] No obvious token/password leaks found."
exit 0
