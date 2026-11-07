<#
.SYNOPSIS
  Runs the balance simulation (test class BalanceSim, plain JVM): 5 player models x grid 6 and 8 x 25 seeds,
  150 moves each. Writes scenarios.csv, moves.csv, summary.md and invariants.txt to the output folder.
  Gradle output goes to build\logs\balance.log. Prints BALANCE OK or BALANCE FAILED and the number of
  invariant failures (each one is a real rule bug: seed and move are in invariants.txt).
.PARAMETER Out
  Output folder. Default: build\balance under the repository root.
.PARAMETER Quiet
  Print only the verdict line.
.EXAMPLE
  .\tools\balance.ps1 -Quiet
.EXAMPLE
  .\tools\balance.ps1 -Out F:\temp\balance
#>
param(
    [string]$Out = '',
    [switch]$Quiet
)
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\env.ps1"
if (-not $Out) { $Out = Join-Path $root 'build\balance' }
$Out = [System.IO.Path]::GetFullPath($Out)
if (Test-Path $Out) { Get-ChildItem -Path $Out -File | Remove-Item -Force }
New-Item -ItemType Directory -Force $Out | Out-Null
$logDir = Join-Path $root 'build\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir 'balance.log'
$init = Join-Path $PSScriptRoot 'balance.gradle'
Push-Location $root
try {
    $gradleOut = & cmd /c ".\gradlew.bat --init-script `"$init`" -Ddonuts.balance.dir=`"$Out`" testDebugUnitTest --tests com.donuts.game.BalanceSim --console=plain 2>&1"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
[System.IO.File]::WriteAllLines($log, [string[]]@($gradleOut))
if (-not $Quiet) { Get-Content $log }
$summary = Join-Path $Out 'summary.md'
if ($code -ne 0 -or -not (Test-Path $summary)) {
    Select-String -Path $log -Pattern '^e: |What went wrong| FAILED$|Exception' -Context 0, 1 |
        Select-Object -First 30 | ForEach-Object { $_.Line }
    Write-Output "BALANCE FAILED (exit $code). Log: $log"
    exit 1
}
$inv = Join-Path $Out 'invariants.txt'
$bad = 0
if (Test-Path $inv) { $bad = @(Get-Content $inv | Where-Object { $_.Trim() }).Count }
Write-Output "BALANCE OK: $summary (invariant failures: $bad)"
