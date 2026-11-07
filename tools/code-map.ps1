<#
.SYNOPSIS
  Maps the Kotlin sources so a function can be read by line range instead of opening a whole file.
  With no parameters it lists every source file with its line count.
.PARAMETER File
  A source file name (for example BoardDraw.kt, or BoardDraw). Lists its functions and types with start lines.
.PARAMETER Match
  A regex on function or type names. Searches every file and prints file:line for each hit.
.EXAMPLE
  .\tools\code-map.ps1
.EXAMPLE
  .\tools\code-map.ps1 -File GameInput.kt
.EXAMPLE
  .\tools\code-map.ps1 -Match "sticker"
#>
param(
    [string]$File,
    [string]$Match,
    [switch]$Doc,
    [string]$UpdateDoc
)
$src = Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\java\com\donuts\game'
$decl = '^\s*(?:(?:private|internal|public|override|inner|data|enum)\s+)*(?:fun|class|object)\s+(?:GameView\.)?(\w+)'

function Get-DocTable([string]$Title, [string]$Dir) {
    "### $Title"
    ''
    '| File | Lines | Symbols (line ranges) |'
    '|---|---|---|'
    if (-not (Test-Path $Dir)) { return }
    foreach ($f in (Get-ChildItem $Dir -Filter *.kt | Sort-Object Name)) {
        $lines = @(Get-Content $f.FullName)
        $total = $lines.Count
        $found = New-Object System.Collections.ArrayList
        for ($i = 0; $i -lt $total; $i++) {
            if ($lines[$i] -match $decl) { [void]$found.Add(@($Matches[1], ($i + 1))) }
        }
        $parts = for ($k = 0; $k -lt $found.Count; $k++) {
            $end = if ($k + 1 -lt $found.Count) { $found[$k + 1][1] - 1 } else { $total }
            '`{0}` {1}-{2}' -f $found[$k][0], $found[$k][1], $end
        }
        '| {0} | {1} | {2} |' -f $f.Name, $total, ($parts -join ', ')
    }
}
function Get-DocMap {
    $root = Split-Path -Parent $PSScriptRoot
    $out = @()
    $out += Get-DocTable 'Main sources' (Join-Path $root 'app\src\main\java\com\donuts\game')
    $out += ''
    $out += Get-DocTable 'Tests' (Join-Path $root 'app\src\test\java\com\donuts\game')
    return $out
}
if ($Doc) { Get-DocMap; exit 0 }
if ($UpdateDoc) {
    $path = (Resolve-Path -LiteralPath $UpdateDoc -ErrorAction Stop).Path
    $text = [System.IO.File]::ReadAllText($path)
    $m1 = '<!-- doc-map:start -->'; $m2 = '<!-- doc-map:end -->'
    $a = $text.IndexOf($m1)
    $b = if ($a -ge 0) { $text.IndexOf($m2, $a + $m1.Length) } else { -1 }
    if ($a -lt 0 -or $b -lt 0) {
        Write-Host "Missing marker in ${path}: need $m1 then $m2"
        exit 1
    }
    $eol = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $body = (Get-DocMap) -join $eol
    $new = $text.Substring(0, $a + $m1.Length) + $eol + $body + $eol + $text.Substring($b)
    if ($new -ceq $text) { Write-Host 'unchanged'; exit 0 }
    [System.IO.File]::WriteAllText($path, $new, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "updated $path"
    exit 0
}

if (-not $File -and -not $Match) {
    Get-ChildItem $src -Filter *.kt | Sort-Object Name | ForEach-Object {
        '{0,5}  {1}' -f (Get-Content $_.FullName).Count, $_.Name
    }
    exit 0
}
if ($File -and -not $File.EndsWith('.kt')) { $File += '.kt' }
$files = if ($File) { Get-Item (Join-Path $src $File) } else { Get-ChildItem $src -Filter *.kt | Sort-Object Name }
foreach ($f in $files) {
    $n = 0
    foreach ($line in Get-Content $f.FullName) {
        $n++
        if ($line -match $decl) {
            $name = $Matches[1]
            if (-not $Match -or $name -match $Match) { '{0}:{1}  {2}' -f $f.Name, $n, $name }
        }
    }
}
