<#
.SYNOPSIS
Tests tools\colorblind.ps1 on a scratch folder: it writes every mode and a sheet, greyscale is grey,
protanopia brings red and green together, -WhatIf writes nothing and a missing folder fails.

.PARAMETER Scratch
Scratch folder to use; deleted and recreated. Default build\test-colorblind.

.EXAMPLE
.\tools\test-colorblind.ps1
#>
param([string]$Scratch = 'build\test-colorblind')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir = if ([System.IO.Path]::IsPathRooted($Scratch)) { $Scratch } else { Join-Path $root $Scratch }
$script = Join-Path $PSScriptRoot 'colorblind.ps1'
$fails = 0
function Test-That([bool]$ok, [string]$what) {
    if ($ok) { Write-Host "ok   $what" } else { Write-Host "FAIL $what"; $script:fails++ }
}

if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
$src = Join-Path $dir 'src'; $out = Join-Path $dir 'out'
New-Item -ItemType Directory -Force -Path $src | Out-Null

Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 4, 2
$bmp.SetPixel(0, 0, [System.Drawing.Color]::FromArgb(200, 60, 40))   # red
$bmp.SetPixel(1, 0, [System.Drawing.Color]::FromArgb(120, 110, 40))  # olive green
$bmp.SetPixel(2, 0, [System.Drawing.Color]::FromArgb(40, 90, 220))   # blue
$bmp.SetPixel(3, 0, [System.Drawing.Color]::FromArgb(255, 255, 255))
$bmp.Save((Join-Path $src 'shot.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

& $script -Source $src -Out $out -WhatIf -Quiet | Out-Null
Test-That (-not (Test-Path $out)) '-WhatIf writes nothing'

& $script -Source $src -Out $out -Quiet | Out-Null
Test-That ($LASTEXITCODE -eq 0) 'exits 0 with no colour collision'
foreach ($m in 'protan', 'deutan', 'tritan', 'grey', 'sheet') {
    Test-That (Test-Path (Join-Path $out "shot__$m.png")) "writes shot__$m.png"
}

$g = New-Object System.Drawing.Bitmap (Join-Path $out 'shot__grey.png')
$p = $g.GetPixel(2, 0); $w = $g.GetPixel(3, 0); $g.Dispose()
Test-That ($p.R -eq $p.G -and $p.G -eq $p.B) 'greyscale pixels are grey'
Test-That ($w.R -eq 255 -and $w.G -eq 255 -and $w.B -eq 255) 'white stays white'

$pr = New-Object System.Drawing.Bitmap (Join-Path $out 'shot__protan.png')
$a = $pr.GetPixel(0, 0); $b = $pr.GetPixel(1, 0); $pr.Dispose()
$dist = [math]::Abs($a.R - $b.R) + [math]::Abs($a.G - $b.G) + [math]::Abs($a.B - $b.B)
Test-That ($dist -lt 65) "protanopia brings red and green together (sum diff $dist, 130 unsimulated)"

$proc = Start-Process powershell -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$script`"", '-Source', "`"$(Join-Path $dir 'missing')`"", '-Out', "`"$out`"", '-Quiet') -NoNewWindow -Wait -PassThru -RedirectStandardError (Join-Path $dir 'err.txt') -RedirectStandardOutput (Join-Path $dir 'out.txt')
Test-That ($proc.ExitCode -ne 0) 'a missing source folder exits non-zero'

Remove-Item -Recurse -Force $dir
if ($fails -gt 0) { Write-Host "test-colorblind: $fails failure(s)"; exit 1 }
Write-Host 'test-colorblind: all passed'
exit 0
