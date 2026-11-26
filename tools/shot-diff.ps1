<#
.SYNOPSIS
Compares two folders of PNGs pixel by pixel and lists every shot that differs.

.DESCRIPTION
Use it around a refactor that must not change the screens: render the gallery into one folder before the
change and another after, then compare. A shot differs when its size or any pixel's ARGB differs; for each
one the script prints how many pixels changed and the bounding box of the change. Shots in only one folder
are listed too. Exits 0 when every shot matches, 1 otherwise. Reads only; changes nothing.

.PARAMETER Before
Folder with the reference PNGs.

.PARAMETER After
Folder with the PNGs to check against them.

.PARAMETER Quiet
Prints only the verdict line and the differing shots.

.EXAMPLE
.\tools\gallery.ps1 -Quiet -Out build\shots\before; .\tools\gallery.ps1 -Quiet -Out build\shots\after; .\tools\shot-diff.ps1 -Before build\shots\before -After build\shots\after -Quiet
#>
param(
    [Parameter(Mandatory = $true)][string]$Before,
    [Parameter(Mandatory = $true)][string]$After,
    [switch]$Quiet
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

foreach ($d in @($Before, $After)) {
    if (-not (Test-Path -LiteralPath $d -PathType Container)) { Write-Output "SHOT-DIFF FAILED: no folder $d"; exit 1 }
}

function Read-Argb([string]$path) {
    $bmp = New-Object System.Drawing.Bitmap $path
    try {
        $rect = New-Object System.Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height
        $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $bytes = New-Object byte[] ($data.Stride * $bmp.Height)
            [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
            return @{ W = $bmp.Width; H = $bmp.Height; Stride = $data.Stride; Bytes = $bytes }
        } finally { $bmp.UnlockBits($data) }
    } finally { $bmp.Dispose() }
}

Add-Type -TypeDefinition @"
public static class ShotDiffNative {
    // Returns { changed pixels, minX, minY, maxX, maxY } for two same-size 32-bit buffers.
    public static int[] Diff(byte[] a, byte[] b, int w, int h, int stride) {
        int n = 0, x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            int row = y * stride;
            for (int x = 0; x < w; x++) {
                int i = row + x * 4;
                if (a[i] != b[i] || a[i + 1] != b[i + 1] || a[i + 2] != b[i + 2] || a[i + 3] != b[i + 3]) {
                    n++;
                    if (x < x0) x0 = x; if (y < y0) y0 = y; if (x > x1) x1 = x; if (y > y1) y1 = y;
                }
            }
        }
        return new int[] { n, x0, y0, x1, y1 };
    }
}
"@

$a = @{}; Get-ChildItem -LiteralPath $Before -Filter '*.png' | ForEach-Object { $a[$_.Name] = $_.FullName }
$b = @{}; Get-ChildItem -LiteralPath $After -Filter '*.png' | ForEach-Object { $b[$_.Name] = $_.FullName }
$bad = New-Object System.Collections.Generic.List[string]
$same = 0
foreach ($name in ($a.Keys | Sort-Object)) {
    if (-not $b.ContainsKey($name)) { $bad.Add("$name only in -Before"); continue }
    $p = Read-Argb $a[$name]; $q = Read-Argb $b[$name]
    if ($p.W -ne $q.W -or $p.H -ne $q.H) { $bad.Add("$name size $($p.W)x$($p.H) -> $($q.W)x$($q.H)"); continue }
    $r = [ShotDiffNative]::Diff($p.Bytes, $q.Bytes, $p.W, $p.H, $p.Stride)
    if ($r[0] -eq 0) { $same++; if (-not $Quiet) { Write-Output "same  $name" } }
    else { $bad.Add("$name $($r[0]) pixels differ in x $($r[1])-$($r[3]), y $($r[2])-$($r[4])") }
}
foreach ($name in ($b.Keys | Sort-Object)) { if (-not $a.ContainsKey($name)) { $bad.Add("$name only in -After") } }

if ($a.Count -eq 0) { Write-Output "SHOT-DIFF FAILED: no PNGs in $Before"; exit 1 }
if ($bad.Count -eq 0) { Write-Output "SHOT-DIFF OK: $same shots identical"; exit 0 }
Write-Output "SHOT-DIFF FAILED: $($bad.Count) of $($a.Count) shots differ ($same identical)"
$bad | ForEach-Object { Write-Output "  $_" }
exit 1
