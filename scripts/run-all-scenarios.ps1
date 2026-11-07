# Runs benchmark scenarios S1-S7 sequentially via experiment-runner (one experiment at a time).
# Usage: .\scripts\run-all-scenarios.ps1 [-RunnerUrl http://localhost:8090] [-Scenarios S6,S7]
# The canonical dataset (5k products / 10k orders) is restored before every scenario, so no
# scenario is measured against data left behind by a previous one. S6 is last on purpose: it is the
# only scenario that injects faults (kills the Mongo primary / stops Redis), and its recovery window
# would otherwise contaminate the next measurement.
param(
    [string]$RunnerUrl = "http://localhost:8090",
    [string]$Scenarios = "S1,S2,S3,S4,S5,S7,S6"
)

$ErrorActionPreference = "Stop"

$presets = @{
    S1 = @{ datasetSize = 5000; clients = 50;  durationSec = 30; repetitions = 3; targets = @("nosql-blocking","relational-normalized","relational-jsonb") }
    S2 = @{ datasetSize = 5000; clients = 50;  durationSec = 30; repetitions = 3; targets = @("mongo-no-cache","mongo-cache","relational-no-cache","relational-cache") }
    S3 = @{ datasetSize = 5000; clients = 100; durationSec = 30; repetitions = 3; targets = @("mongo-w1","mongo-majority","postgres") }
    S4 = @{ datasetSize = 5000; clients = 20;  durationSec = 30; repetitions = 3; targets = @("mongo-indexed","mongo-unindexed","postgres-indexed","postgres-unindexed") }
    S5 = @{ datasetSize = 5000; clients = 2000; durationSec = 60; repetitions = 3; targets = @("nosql-blocking","nosql-reactive") }
    S6 = @{ datasetSize = 5000; clients = 50;  durationSec = 30; repetitions = 3; targets = @("failover","redis-down","concurrency") }
    S7 = @{ datasetSize = 5000; clients = 2;   durationSec = 90; repetitions = 3; targets = @("nosql-blocking","relational") }
}

function Wait-Runner {
    param([string]$Url, [int]$MaxWaitSec = 120)
    $deadline = (Get-Date).AddSeconds($MaxWaitSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri "$Url/api/live" -TimeoutSec 5
            if ($r.services | Where-Object { $_.status -eq "UP" }) { return }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw "Runner or services not ready at $Url within ${MaxWaitSec}s"
}

$composeFile = (Resolve-Path (Join-Path $PSScriptRoot "..\docker-compose.yml")).Path

# Every container a scenario can touch. A scenario must never be measured while one of these is
# missing, stopped or unhealthy: an OOM kill or a crashed service would otherwise be recorded as a
# "successful" run with meaningless throughput and error rates.
$requiredContainers = @(
    "mongo1", "mongo2", "mongo3", "redis", "postgres",
    "stub-service", "experiment-runner",
    "nosql-blocking", "nosql-reactive", "nosql-blocking-w1", "relational", "relational-jsonb"
)

function Assert-ServicesHealthy {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $bad = @()
        foreach ($name in $requiredContainers) {
            # Capture the native exit code immediately: piping into Select-Object -First 1 aborts the
            # pipeline early and leaves $LASTEXITCODE unreliable, which produced false "missing" hits.
            $raw = docker inspect $name --format "{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" 2>$null
            $code = $LASTEXITCODE
            if ($code -ne 0 -or -not $raw) { $bad += "$name (missing)"; continue }
            $state = [string]($raw | Select-Object -First 1)
            $parts = $state.Trim().Split("|")
            if ($parts[0] -ne "true") { $bad += "$name (not running)"; continue }
            if ($parts.Count -gt 1 -and $parts[1] -ne "none" -and $parts[1] -ne "healthy") {
                $bad += "$name (health=$($parts[1]))"
            }
        }
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($bad.Count -gt 0) {
        throw "Precondition failed - these containers are not healthy: $($bad -join ', '). Fix them and re-run."
    }
}

function Reset-Dataset {
    Write-Host "  Restoring canonical dataset (5k products / 10k orders)..." -ForegroundColor DarkGray
    # docker compose writes progress to stderr; with $ErrorActionPreference = "Stop" PowerShell 5
    # would treat that as a terminating NativeCommandError, so relax it just for this call.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = docker compose -f $composeFile run --rm data-generator --reset 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) {
        Write-Host ($out -join "`n")
        throw "Dataset reset failed (exit code $code)"
    }
}

function Start-Scenario {
    param([string]$Code, [hashtable]$Preset)
    $body = @{
        scenario    = $Code
        datasetSize = $Preset.datasetSize
        clients     = $Preset.clients
        durationSec = $Preset.durationSec
        repetitions = $Preset.repetitions
        targets     = $Preset.targets
    } | ConvertTo-Json -Depth 5

    # A transient socket error ("underlying connection was closed") can be raised after the runner
    # already accepted the POST. The runner allows only one experiment at a time and rejects
    # concurrent ones with 409, so before retrying we look for an already-active run and reuse it
    # instead of launching a duplicate measurement for the same scenario.
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            return Invoke-RestMethod -Method Post -Uri "$RunnerUrl/api/experiments" -ContentType "application/json" -Body $body -TimeoutSec 30
        } catch {
            Write-Warning "POST /api/experiments for $Code failed (attempt $attempt/3): $($_.Exception.Message)"
            if ($attempt -ge 3) { throw }
            Start-Sleep -Seconds 5
            try {
                $active = Invoke-RestMethod -Uri "$RunnerUrl/api/experiments" -TimeoutSec 30 |
                    Where-Object { $_.status -in @("PENDING", "RUNNING") } | Select-Object -First 1
            } catch { $active = $null }
            if ($active) {
                Write-Warning "An experiment is already active ($($active.id)); assuming the earlier POST was accepted and reusing it."
                return $active
            }
        }
    }
}

function Wait-Experiment {
    param([string]$Id)
    while ($true) {
        $run = $null
        for ($i = 1; $i -le 4; $i++) {
            try { $run = Invoke-RestMethod -Uri "$RunnerUrl/api/experiments/$Id" -TimeoutSec 30; break }
            catch {
                if ($i -ge 4) { throw }
                Write-Warning "GET /api/experiments/$Id failed (attempt $i/4): $($_.Exception.Message)"
                Start-Sleep -Seconds 5
            }
        }
        $status = $run.status
        $step = $run.progress.currentStep
        Write-Host "  [$Id] $status - $step"
        if ($status -in @("COMPLETED", "FAILED", "CANCELLED")) { return $run }
        Start-Sleep -Seconds 10
    }
}

Wait-Runner -Url $RunnerUrl

$codes = $Scenarios.Split(",") | ForEach-Object { $_.Trim().ToUpper() } | Where-Object { $_ }
$results = @()

foreach ($code in $codes) {
    if (-not $presets.ContainsKey($code)) {
        Write-Warning "Unknown scenario $code - skipped"
        continue
    }
    Write-Host "`n=== Starting $code ===" -ForegroundColor Cyan
    Assert-ServicesHealthy
    Reset-Dataset
    $started = Start-Scenario -Code $code -Preset $presets[$code]
    $finished = Wait-Experiment -Id $started.id
    $results += [pscustomobject]@{
        Scenario = $code
        RunId    = $finished.id
        Status   = $finished.status
        Result   = $finished.resultFile
    }
    if ($finished.status -ne "COMPLETED") {
        Write-Warning "$code finished with status $($finished.status)"
    }
}

Write-Host "`n=== Summary ===" -ForegroundColor Green
$results | Format-Table -AutoSize | Out-String | Write-Host
if ($results.Count -eq 0) {
    Write-Error "No scenarios were executed (check -Scenarios parameter)."
    exit 1
}
