<#
.SYNOPSIS
  Runs the unit tests with JaCoCo coverage and prints line and branch coverage per source file.
  Coverage is switched on by the init script tools\coverage.gradle, so app\build.gradle is unchanged.
  The table also goes to build\logs\coverage.txt; the Gradle output to build\logs\coverage.log.
.PARAMETER Quiet
  Print only the summary line and the table, not the Gradle output.
.PARAMETER Lines
  Also list the uncovered line numbers of each file (written to build\logs\coverage-lines.txt).
.EXAMPLE
  .\tools\coverage.ps1 -Quiet
.EXAMPLE
  .\tools\coverage.ps1 -Quiet -Lines
#>
param(
    [switch]$Quiet,
    [switch]$Lines
)
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\env.ps1"
$logDir = Join-Path $root 'build\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir 'coverage.log'
$init = Join-Path $PSScriptRoot 'coverage.gradle'

Push-Location $root
try {
    $out = & cmd /c ".\gradlew.bat --init-script `"$init`" createDebugUnitTestCoverageReport --console=plain 2>&1"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
[System.IO.File]::WriteAllLines($log, [string[]]@($out))
if (-not $Quiet) { Get-Content $log }
if ($code -ne 0) {
    Select-String -Path $log -Pattern '^e: |What went wrong| FAILED$' -Context 0, 1 |
        Select-Object -First 30 | ForEach-Object { $_.Line }
    Write-Output "COVERAGE FAILED (exit $code). Log: $log"
    exit 1
}

$xmlPath = Get-ChildItem -Path (Join-Path $root 'app\build\reports\coverage') -Recurse -Filter 'report.xml' |
    Select-Object -First 1
if (-not $xmlPath) { Write-Output "COVERAGE FAILED: no report.xml under app\build\reports\coverage"; exit 1 }

$settings = New-Object System.Xml.XmlReaderSettings
$settings.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
$reader = [System.Xml.XmlReader]::Create($xmlPath.FullName, $settings)
$xml = New-Object System.Xml.XmlDocument
$xml.Load($reader)
$reader.Close()

function Get-Counter($node, $type) {
    $c = $node.SelectSingleNode("counter[@type='$type']")
    if ($c) { return @([int]$c.missed, [int]$c.covered) }
    return @(0, 0)
}

$rows = @()
$missing = @()
foreach ($sf in $xml.SelectNodes('//package/sourcefile')) {
    $l = Get-Counter $sf 'LINE'
    $b = Get-Counter $sf 'BRANCH'
    $total = $l[0] + $l[1]
    if ($total -eq 0) { continue }
    $bt = $b[0] + $b[1]
    $rows += [pscustomobject]@{
        File      = $sf.name
        Lines     = $total
        LinePct   = [math]::Round(100.0 * $l[1] / $total)
        Branches  = $bt
        BranchPct = $(if ($bt -gt 0) { [math]::Round(100.0 * $b[1] / $bt) } else { 100 })
    }
    if ($Lines) {
        $nums = @($sf.SelectNodes('line') | Where-Object { [int]$_.ci -eq 0 } | ForEach-Object { $_.nr })
        if ($nums.Count -gt 0) { $missing += "$($sf.name): $($nums -join ',')" }
    }
}
$rows = $rows | Sort-Object LinePct, File
$table = $rows | Format-Table -AutoSize | Out-String
$report = $xml.SelectSingleNode('/report')
$tl = Get-Counter $report 'LINE'
$tb = Get-Counter $report 'BRANCH'
$summary = "COVERAGE OK: lines {0}% ({1}/{2}), branches {3}% ({4}/{5})" -f `
    [math]::Round(100.0 * $tl[1] / [math]::Max(1, $tl[0] + $tl[1])), $tl[1], ($tl[0] + $tl[1]),
    [math]::Round(100.0 * $tb[1] / [math]::Max(1, $tb[0] + $tb[1])), $tb[1], ($tb[0] + $tb[1])
[System.IO.File]::WriteAllText((Join-Path $logDir 'coverage.txt'), $summary + "`r`n" + $table)
if ($Lines) { [System.IO.File]::WriteAllLines((Join-Path $logDir 'coverage-lines.txt'), [string[]]$missing) }
Write-Output $table.TrimEnd()
Write-Output $summary
exit 0
