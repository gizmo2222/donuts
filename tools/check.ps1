<#
.SYNOPSIS
  Builds the app and prints one verdict line. On failure it lists the compiler errors.
  The full Gradle output always goes to build\logs\check.log.
.PARAMETER Quiet
  Print only the verdict line (and the error lines when the build fails).
.PARAMETER Task
  The Gradle task to run. Default: assembleDebug.
.PARAMETER Test
  Run the unit tests (testDebugUnitTest) instead. Ignored when -Task is given.
.PARAMETER Tests
  Gradle --tests filters, for example 'com.donuts.game.GameInputTest'. Implies -Test. Default: the whole suite.
.EXAMPLE
  .\tools\check.ps1 -Quiet
.EXAMPLE
  .\tools\check.ps1 -Test -Quiet
.EXAMPLE
  .\tools\check.ps1 -Quiet -Tests 'com.donuts.game.GameInputTest','com.donuts.game.ChainRulesTest'
#>
param(
    [switch]$Quiet,
    [switch]$Test,
    [string[]]$Tests = @(),
    [string]$Task = 'assembleDebug'
)
if ($Tests.Count -gt 0) { $Test = $true }
if ($Test -and -not $PSBoundParameters.ContainsKey('Task')) { $Task = 'testDebugUnitTest' }
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\env.ps1"
$logDir = Join-Path $root 'build\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir 'check.log'

Push-Location $root
try {
    # cmd merges stderr into stdout so PowerShell 5.1 does not wrap those lines in error records
    $filter = ($Tests | ForEach-Object { "--tests $_" }) -join ' '
    $out = & cmd /c ".\gradlew.bat $Task $filter --console=plain 2>&1"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
[System.IO.File]::WriteAllLines($log, [string[]]@($out))

if (-not $Quiet) { Get-Content $log }
$warnings = @(Select-String -Path $log -Pattern '^w: ').Count
if ($code -eq 0) {
    Write-Output "CHECK OK: $Task ($warnings warnings). Log: $log"
    exit 0
}
Select-String -Path $log -Pattern '^e: |^FAILURE|What went wrong| FAILED$' -Context 0, 1 |
    Select-Object -First 40 | ForEach-Object { $_.Line }
Write-Output "CHECK FAILED: $Task (exit $code). Log: $log"
exit 1
