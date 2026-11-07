<#
.SYNOPSIS
  Mutation check: makes one small change at a time to a source file (== to !=, < to <=, && to ||,
  true to false, + to -, until to .., dropped !), runs the unit tests, and reports which changes no test
  caught ("survived"). The file is always put back, even after Ctrl+C (a backup in build\logs is restored
  on the next run, or by -WhatIf, unless the file was edited since). Report: build\logs\mutate-<File>.txt.
  Gradle runs with --daemon --offline for speed.
.PARAMETER File
  Source file name under app\src\main\java\com\donuts\game, for example GameBoard.kt.
.PARAMETER Tests
  Gradle --tests filters, for example 'com.donuts.game.GameBoardTest'. Default: the whole suite.
.PARAMETER Max
  Most mutants to run; when there are more, an evenly spread sample is taken. Default 30.
.PARAMETER From
  First line to mutate (default 1). Use the doc map's line ranges to target one function.
.PARAMETER To
  Last line to mutate (default: end of file).
.PARAMETER WhatIf
  List the mutants that would run, without running tests. Also restores a file left mutated by an interrupted run.
.EXAMPLE
  .\tools\mutate.ps1 -File ChainRules.kt -Tests 'com.donuts.game.ChainRulesTest'
.EXAMPLE
  .\tools\mutate.ps1 -File GameBoard.kt -From 223 -To 301 -Max 20 -WhatIf
#>
param(
    [Parameter(Mandatory = $true)][string]$File,
    [string[]]$Tests = @(),
    [int]$Max = 30,
    [int]$From = 1,
    [int]$To = [int]::MaxValue,
    [switch]$WhatIf
)
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\env.ps1"
$src = Join-Path $root "app\src\main\java\com\donuts\game\$File"
if (-not (Test-Path $src)) { Write-Output "MUTATE FAILED: no such file $src"; exit 1 }
$logDir = Join-Path $root 'build\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$backup = Join-Path $logDir "mutate-backup-$File"
$report = Join-Path $logDir "mutate-$File.txt"
$utf8 = New-Object System.Text.UTF8Encoding($false)

# A backup left by an interrupted run is the real file: put it back first, but only when the file on disk
# is that backup plus the one leftover mutant. Anything else means the file was edited since: keep both.
if (Test-Path $backup) {
    $old = [System.IO.File]::ReadAllText($backup) -split "`n"
    $now = [System.IO.File]::ReadAllText($src) -split "`n"
    $diff = 0
    if ($old.Count -eq $now.Count) { for ($k = 0; $k -lt $old.Count; $k++) { if ($old[$k] -ne $now[$k]) { $diff++ } } }
    if ($old.Count -ne $now.Count -or $diff -gt 1) {
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $kept = "$backup.$stamp"
        Move-Item $backup $kept
        Write-Output "MUTATE FAILED: $File changed since an interrupted run left a backup. Nothing was overwritten."
        Write-Output "Compare $src with $kept and keep the right one, then delete $kept."
        exit 1
    }
    [System.IO.File]::WriteAllBytes($src, [System.IO.File]::ReadAllBytes($backup))
    Remove-Item $backup -Force
    Write-Output "Restored $File from an interrupted run."
}
$origBytes = [System.IO.File]::ReadAllBytes($src)
$text = $utf8.GetString($origBytes)
if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }
$lines = $text -split "`n"

$ops = @(
    @{ Find = ' == ';  Repl = ' != ' }, @{ Find = ' != ';  Repl = ' == ' },
    @{ Find = ' < ';   Repl = ' <= ' }, @{ Find = ' <= ';  Repl = ' < ' },
    @{ Find = ' > ';   Repl = ' >= ' }, @{ Find = ' >= ';  Repl = ' > ' },
    @{ Find = ' && ';  Repl = ' || ' }, @{ Find = ' || ';  Repl = ' && ' },
    @{ Find = ' + ';   Repl = ' - ' },  @{ Find = ' - ';   Repl = ' + ' },
    @{ Find = ' until '; Repl = ' .. ' },
    @{ Find = 'true';  Repl = 'false'; Word = $true }, @{ Find = 'false'; Repl = 'true'; Word = $true },
    @{ Find = '!';     Repl = ''; Bang = $true }
)

function Test-InString([string]$line, [int]$pos) {
    $q = 0
    for ($i = 0; $i -lt $pos; $i++) { if ($line[$i] -eq '"' -and ($i -eq 0 -or $line[$i - 1] -ne '\')) { $q++ } }
    return ($q % 2) -eq 1
}

$mutants = New-Object System.Collections.ArrayList
for ($n = [math]::Max(1, $From); $n -le [math]::Min($To, $lines.Count); $n++) {
    $line = $lines[$n - 1]
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('//') -or $t.StartsWith('*') -or $t.StartsWith('/*') -or
        $t.StartsWith('import ') -or $t.StartsWith('package ')) { continue }
    $code = $line
    $cmt = $line.IndexOf('//')
    while ($cmt -ge 0 -and (Test-InString $line $cmt)) { $cmt = $line.IndexOf('//', $cmt + 2) }
    if ($cmt -ge 0) { $code = $line.Substring(0, $cmt) }
    foreach ($op in $ops) {
        $start = 0
        while (($pos = $code.IndexOf($op.Find, $start)) -ge 0) {
            $start = $pos + 1
            if (Test-InString $code $pos) { continue }
            if ($op.Word) {
                $before = if ($pos -gt 0) { $code[$pos - 1] } else { ' ' }
                $afterI = $pos + $op.Find.Length
                $after = if ($afterI -lt $code.Length) { $code[$afterI] } else { ' ' }
                if ([char]::IsLetterOrDigit($before) -or $before -eq '_' -or
                    [char]::IsLetterOrDigit($after) -or $after -eq '_') { continue }
            }
            if ($op.Bang) {
                # Only a logical not in front of a name or a bracket: not !=, not !!
                $next = if ($pos + 1 -lt $code.Length) { $code[$pos + 1] } else { ' ' }
                $prev = if ($pos -gt 0) { $code[$pos - 1] } else { ' ' }
                if ($prev -eq '!' -or -not ([char]::IsLetter($next) -or $next -eq '(')) { continue }
            }
            $mutated = $line.Substring(0, $pos) + $op.Repl + $line.Substring($pos + $op.Find.Length)
            [void]$mutants.Add([pscustomobject]@{
                Line = $n; Op = "'$($op.Find.Trim())' -> '$($op.Repl.Trim())'"; Mutated = $mutated
                Before = $t; Result = '' })
        }
    }
}
$total = $mutants.Count
if ($total -eq 0) { Write-Output "MUTATE: no mutants in $File lines $From-$To"; exit 0 }
if ($total -gt $Max) {
    $picked = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $Max; $i++) { [void]$picked.Add($mutants[[math]::Floor($i * $total / $Max)]) }
    $mutants = $picked
}
if ($WhatIf) {
    $mutants | ForEach-Object { "{0,5}  {1,-16} {2}" -f $_.Line, $_.Op, $_.Mutated.Trim() }
    Write-Output "MUTATE (WhatIf): $($mutants.Count) of $total mutants in $File"
    exit 0
}

$filter = ($Tests | ForEach-Object { "--tests $_" }) -join ' '
function Invoke-Tests {
    Push-Location $root
    try {
        $out = & cmd /c ".\gradlew.bat testDebugUnitTest $filter --daemon --offline -Pkotlin.compiler.execution.strategy=daemon --console=plain -q 2>&1"
        $code = $LASTEXITCODE
    } finally { Pop-Location }
    return @{ Code = $code; Out = ($out -join "`n") }
}

$base = Invoke-Tests
if ($base.Code -ne 0) {
    [System.IO.File]::WriteAllText((Join-Path $logDir "mutate-baseline.log"), $base.Out)
    Write-Output "MUTATE FAILED: the tests fail before any change. Log: build\logs\mutate-baseline.log"
    exit 1
}

[System.IO.File]::WriteAllBytes($backup, $origBytes)
try {
    $i = 0
    foreach ($m in $mutants) {
        $i++
        $copy = [string[]]$lines.Clone()
        $copy[$m.Line - 1] = $m.Mutated
        [System.IO.File]::WriteAllText($src, ($copy -join "`n"), $utf8)
        $r = Invoke-Tests
        if ($r.Code -eq 0) { $m.Result = 'SURVIVED' }
        elseif ($r.Out -match '(?m)^e: |compile\w*Kotlin.* FAILED|Compilation error') { $m.Result = 'NOCOMPILE' }
        else { $m.Result = 'KILLED' }
        Write-Output ("[{0}/{1}] {2,-9} line {3}: {4}" -f $i, $mutants.Count, $m.Result, $m.Line, $m.Op)
    }
} finally {
    [System.IO.File]::WriteAllBytes($src, $origBytes)
    Remove-Item $backup -Force -ErrorAction SilentlyContinue
}

$killed = @($mutants | Where-Object Result -eq 'KILLED').Count
$survived = @($mutants | Where-Object Result -eq 'SURVIVED')
$nocomp = @($mutants | Where-Object Result -eq 'NOCOMPILE').Count
$valid = $killed + $survived.Count
$score = if ($valid -gt 0) { [math]::Round(100.0 * $killed / $valid) } else { 100 }
$summary = "MUTATE: $File, $($mutants.Count) of $total mutants run: $killed killed, $($survived.Count) survived, $nocomp did not compile. Score $score%"
$body = @($summary, '', 'Survivors (no test failed):') +
    @($survived | ForEach-Object { "  line {0}: {1}`n    was: {2}`n    now: {3}" -f $_.Line, $_.Op, $_.Before, $_.Mutated.Trim() })
[System.IO.File]::WriteAllLines($report, [string[]]$body)
$body | Select-Object -Skip 2
Write-Output $summary
exit 0
