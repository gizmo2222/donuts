<#
.SYNOPSIS
  Maps the Kotlin sources so a function can be read by line range instead of opening a whole file.
  With no parameters it lists every source file with its line count.
.PARAMETER File
  A source file name (for example BoardDraw.kt). Lists its functions and types with start lines.
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
    [string]$Match
)
$src = Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\java\com\donuts\game'
$decl = '^\s*(?:(?:private|internal|public|override|inner|data|enum)\s+)*(?:fun|class|object)\s+(?:GameView\.)?(\w+)'

if (-not $File -and -not $Match) {
    Get-ChildItem $src -Filter *.kt | Sort-Object Name | ForEach-Object {
        '{0,5}  {1}' -f (Get-Content $_.FullName).Count, $_.Name
    }
    exit 0
}
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
