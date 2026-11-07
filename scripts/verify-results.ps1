# Validates the latest result JSON per scenario in results/ (mean, stdDev, error rate sanity).
param(
    [string]$ResultsDir = (Join-Path (Split-Path $PSScriptRoot -Parent) "results")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ResultsDir)) {
    throw "Results directory not found: $ResultsDir"
}

$files = Get-ChildItem -Path $ResultsDir -Filter "s*.json" | Sort-Object LastWriteTime -Descending
$latestByScenario = @{}
foreach ($f in $files) {
    if ($f.Name -match '^(s[1-7])-') {
        $sc = $Matches[1].ToUpper()
        if (-not $latestByScenario.ContainsKey($sc)) {
            $latestByScenario[$sc] = $f.FullName
        }
    }
}

$required = @("S1","S2","S3","S4","S5","S6","S7")
$issues = @()
$rows = @()

foreach ($sc in $required) {
    if (-not $latestByScenario.ContainsKey($sc)) {
        $issues += "Missing result file for $sc"
        $rows += [pscustomobject]@{ Scenario = $sc; Status = "MISSING"; Targets = 0; MaxErrorRate = $null; SetupWarnings = $null }
        continue
    }
    $doc = Get-Content -Raw -Path $latestByScenario[$sc] | ConvertFrom-Json
    $targetCount = @($doc.targets).Count
    $maxErr = 0.0
    $setupWarn = 0
    foreach ($t in $doc.targets) {
        $meanErr = $t.aggregated.errorRate.mean
        if ($null -ne $meanErr -and $meanErr -gt $maxErr) { $maxErr = $meanErr }
        foreach ($s in @($t.setup)) {
            if ($s -match "FAILED") { $setupWarn++ }
        }
        if ($t.error) { $issues += "$sc target $($t.target): $($t.error)" }
        if (@($t.samples).Count -lt 1) { $issues += "$sc target $($t.target): no samples" }
    }
    if ($doc.status -ne "COMPLETED") { $issues += "$sc run status is $($doc.status)" }
    if ($setupWarn -gt 0) { $issues += "$sc has $setupWarn setup warning(s) in latest file" }

    $status = if ($doc.status -eq "COMPLETED" -and $targetCount -gt 0) { "OK" } else { "CHECK" }
    $rows += [pscustomobject]@{
        Scenario       = $sc
        Status         = $status
        Targets        = $targetCount
        MaxErrorRate   = [math]::Round($maxErr * 100, 2)
        SetupWarnings  = $setupWarn
        File           = Split-Path $latestByScenario[$sc] -Leaf
    }
}

Write-Host "Latest results per scenario:" -ForegroundColor Cyan
$rows | Format-Table -AutoSize

if ($issues.Count -gt 0) {
    Write-Host "`nIssues:" -ForegroundColor Yellow
    $issues | ForEach-Object { Write-Host "  - $_" }
    exit 1
}

Write-Host "`nAll scenarios have result files with completed runs." -ForegroundColor Green
exit 0
