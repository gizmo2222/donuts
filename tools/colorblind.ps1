<#
.SYNOPSIS
Colour-blind check: simulates protanopia, deuteranopia, tritanopia and greyscale over the gallery shots,
and prints how far apart the six donut colours stay under each.

.DESCRIPTION
For every PNG in -Source it writes one PNG per mode into -Out, plus a sheet (<name>__sheet.png) with the
original and every mode side by side at half size. The simulation is Machado, Oliveira and Fernandes (2009) at
full severity, applied in linear RGB; greyscale is the Rec. 709 luminance.

It also reads the body and glaze colours from app\src\main\java\com\donuts\game\DonutType.kt and prints, per
mode, the CIE76 colour distance (Delta E) of the closest donut pairs. A pair collides when both its body and
its glaze distances are under -Threshold; collisions are listed after the table. Greyscale collisions are
reported but not counted, since the six donut shapes carry the difference there. The script exits 1 on a
colour collision only with -FailOnCollision. Run .\tools\gallery.ps1 -Quiet first.

.PARAMETER Source
Folder of PNGs to simulate. Default build\shots\gallery.

.PARAMETER Out
Folder for the simulated PNGs and the sheets. Default build\shots\colorblind. Created if missing.

.PARAMETER Filter
File name filter inside -Source, for example '*02-board*'. Default '*.png'.

.PARAMETER Threshold
Delta E under which a body or glaze pair counts as the same colour. Default 10.

.PARAMETER FailOnCollision
Exit 1 when a pair collides under protanopia, deuteranopia or tritanopia.

.PARAMETER PaletteOnly
Print the palette table only; write no images.

.PARAMETER Quiet
Print the verdict line and any collisions only.

.EXAMPLE
.\tools\colorblind.ps1 -Quiet

.EXAMPLE
.\tools\colorblind.ps1 -Filter '*phone__02-board*'
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Source = 'build\shots\gallery',
    [string]$Out = 'build\shots\colorblind',
    [string]$Filter = '*.png',
    [double]$Threshold = 10,
    [switch]$FailOnCollision,
    [switch]$PaletteOnly,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Add-Type -AssemblyName System.Drawing
if (-not ('DonutsColorBlind' -as [type])) {
    Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class DonutsColorBlind {
    public static readonly string[] Modes = { "protan", "deutan", "tritan", "grey" };

    // Machado et al. 2009, severity 1.0, linear RGB.
    static readonly double[][,] M = {
        new double[,] { { 0.152286, 1.052583, -0.204868 }, { 0.114503, 0.786281, 0.099216 }, { -0.003882, -0.048116, 1.051998 } },
        new double[,] { { 0.367322, 0.860646, -0.227968 }, { 0.280085, 0.672501, 0.047413 }, { -0.011820, 0.042940, 0.968881 } },
        new double[,] { { 1.255528, -0.076749, -0.178779 }, { -0.078411, 0.930809, 0.147602 }, { 0.004733, 0.691367, 0.303900 } },
    };

    static readonly double[] ToLin = BuildToLin();
    static readonly byte[] ToSrgb = BuildToSrgb();

    static double[] BuildToLin() {
        var t = new double[256];
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            t[i] = c <= 0.04045 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4);
        }
        return t;
    }

    static byte[] BuildToSrgb() {
        var t = new byte[4096];
        for (int i = 0; i < 4096; i++) {
            double l = i / 4095.0;
            double c = l <= 0.0031308 ? l * 12.92 : 1.055 * Math.Pow(l, 1 / 2.4) - 0.055;
            t[i] = (byte)Math.Max(0, Math.Min(255, Math.Round(c * 255)));
        }
        return t;
    }

    static double Clamp(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    // Simulated colour in linear RGB.
    public static double[] SimLinear(int r, int g, int b, int mode) {
        double lr = ToLin[r], lg = ToLin[g], lb = ToLin[b];
        if (mode == 3) {
            double y = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
            return new[] { y, y, y };
        }
        if (mode < 0) return new[] { lr, lg, lb };
        var m = M[mode];
        return new[] {
            Clamp(m[0, 0] * lr + m[0, 1] * lg + m[0, 2] * lb),
            Clamp(m[1, 0] * lr + m[1, 1] * lg + m[1, 2] * lb),
            Clamp(m[2, 0] * lr + m[2, 1] * lg + m[2, 2] * lb),
        };
    }

    static byte Enc(double l) { return ToSrgb[(int)Math.Round(Clamp(l) * 4095)]; }

    public static int[] Sim(int r, int g, int b, int mode) {
        var l = SimLinear(r, g, b, mode);
        return new int[] { Enc(l[0]), Enc(l[1]), Enc(l[2]) };
    }

    static double F(double t) { return t > 0.008856 ? Math.Pow(t, 1.0 / 3) : 7.787 * t + 16.0 / 116; }

    // CIE76 Delta E between two sRGB colours after simulating both with mode (-1 = none).
    public static double DeltaE(int[] a, int[] b, int mode) {
        var la = Lab(SimLinear(a[0], a[1], a[2], mode));
        var lb = Lab(SimLinear(b[0], b[1], b[2], mode));
        double d0 = la[0] - lb[0], d1 = la[1] - lb[1], d2 = la[2] - lb[2];
        return Math.Sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    static double[] Lab(double[] l) {
        double x = 0.4124 * l[0] + 0.3576 * l[1] + 0.1805 * l[2];
        double y = 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2];
        double z = 0.0193 * l[0] + 0.1192 * l[1] + 0.9505 * l[2];
        double fx = F(x / 0.95047), fy = F(y), fz = F(z / 1.08883);
        return new[] { 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz) };
    }

    // Writes one PNG per mode next to outBase (outBase + "__" + mode + ".png") and a half-size sheet.
    public static void SimulateFile(string src, string outBase) {
        using (var orig = new Bitmap(src)) {
            int w = orig.Width, h = orig.Height;
            var outs = new Bitmap[Modes.Length];
            using (var argb = orig.Clone(new Rectangle(0, 0, w, h), PixelFormat.Format32bppArgb)) {
                var rect = new Rectangle(0, 0, w, h);
                var sd = argb.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
                var px = new byte[sd.Stride * h];
                Marshal.Copy(sd.Scan0, px, 0, px.Length);
                argb.UnlockBits(sd);
                for (int m = 0; m < Modes.Length; m++) {
                    var o = new byte[px.Length];
                    for (int i = 0; i < px.Length; i += 4) {
                        var s = Sim(px[i + 2], px[i + 1], px[i], m);
                        o[i] = (byte)s[2]; o[i + 1] = (byte)s[1]; o[i + 2] = (byte)s[0]; o[i + 3] = px[i + 3];
                    }
                    var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
                    var od = bmp.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
                    Marshal.Copy(o, 0, od.Scan0, o.Length);
                    bmp.UnlockBits(od);
                    bmp.Save(outBase + "__" + Modes[m] + ".png", ImageFormat.Png);
                    outs[m] = bmp;
                }
                int sw = Math.Max(1, w / 2), sh = Math.Max(1, h / 2), gap = 8;
                using (var sheet = new Bitmap(sw * (Modes.Length + 1) + gap * Modes.Length, sh, PixelFormat.Format32bppArgb))
                using (var g = Graphics.FromImage(sheet)) {
                    g.Clear(Color.White);
                    g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBilinear;
                    g.DrawImage(argb, 0, 0, sw, sh);
                    for (int m = 0; m < Modes.Length; m++) g.DrawImage(outs[m], (sw + gap) * (m + 1), 0, sw, sh);
                    sheet.Save(outBase + "__sheet.png", ImageFormat.Png);
                }
            }
            foreach (var b in outs) b.Dispose();
        }
    }
}
'@
}

# ---- Palette: the body and glaze colours of each DonutType ----
$typeFile = Join-Path $root 'app\src\main\java\com\donuts\game\DonutType.kt'
if (-not (Test-Path $typeFile)) { Write-Error "Not found: $typeFile"; exit 1 }
$rx = '^\s*([A-Z]+)\s*\(\s*Color\.rgb\(\s*(\d+),\s*(\d+),\s*(\d+)\),\s*Color\.rgb\(\s*(\d+),\s*(\d+),\s*(\d+)\)'
$types = @()
foreach ($line in [System.IO.File]::ReadAllLines($typeFile)) {
    $m = [regex]::Match($line, $rx)
    if ($m.Success) {
        $types += [pscustomobject]@{
            Name  = $m.Groups[1].Value
            Body  = [int[]]@([int]$m.Groups[2].Value, [int]$m.Groups[3].Value, [int]$m.Groups[4].Value)
            Glaze = [int[]]@([int]$m.Groups[5].Value, [int]$m.Groups[6].Value, [int]$m.Groups[7].Value)
        }
    }
}
if ($types.Count -lt 2) { Write-Error "Could not read the donut colours from $typeFile"; exit 1 }

$modeNames = @('normal') + [DonutsColorBlind]::Modes
$collisions = @()
$table = @()
for ($mi = 0; $mi -lt $modeNames.Count; $mi++) {
    $mode = $mi - 1   # -1 = normal
    $pairs = @()
    for ($a = 0; $a -lt $types.Count; $a++) {
        for ($b = $a + 1; $b -lt $types.Count; $b++) {
            $db = [DonutsColorBlind]::DeltaE($types[$a].Body, $types[$b].Body, $mode)
            $dg = [DonutsColorBlind]::DeltaE($types[$a].Glaze, $types[$b].Glaze, $mode)
            $pair = [pscustomobject]@{
                Mode = $modeNames[$mi]; Pair = "$($types[$a].Name)/$($types[$b].Name)"
                Body = [math]::Round($db, 1); Glaze = [math]::Round($dg, 1); Max = [math]::Max($db, $dg)
            }
            $pairs += $pair
            if ($db -lt $Threshold -and $dg -lt $Threshold) { $collisions += $pair }
        }
    }
    $table += $pairs | Sort-Object Max | Select-Object -First 3
}

if (-not $Quiet) {
    Write-Host "Closest donut pairs per mode (CIE76 Delta E, body and glaze; collide when both < $Threshold):"
    $table | Format-Table Mode, Pair, Body, Glaze -AutoSize | Out-String | Write-Host
}

# ---- Images ----
$written = 0
if (-not $PaletteOnly) {
    $srcDir = if ([System.IO.Path]::IsPathRooted($Source)) { $Source } else { Join-Path $root $Source }
    $outDir = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $root $Out }
    if (-not (Test-Path $srcDir)) { Write-Error "No shots in $srcDir; run .\tools\gallery.ps1 -Quiet first"; exit 1 }
    $files = @(Get-ChildItem -Path $srcDir -Filter $Filter -File | Where-Object { $_.Extension -eq '.png' })
    if ($files.Count -eq 0) { Write-Error "No PNG matches '$Filter' in $srcDir"; exit 1 }
    if ($PSCmdlet.ShouldProcess($outDir, "write $($files.Count * 5) simulated PNGs")) {
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        foreach ($f in $files) {
            [DonutsColorBlind]::SimulateFile($f.FullName, (Join-Path $outDir $f.BaseName))
            $written++
        }
    }
}

$hard = @($collisions | Where-Object { $_.Mode -ne 'grey' -and $_.Mode -ne 'normal' })
$soft = @($collisions | Where-Object { $_.Mode -eq 'grey' })
foreach ($c in $hard) { Write-Host ("COLLISION {0}: {1} (body {2}, glaze {3})" -f $c.Mode, $c.Pair, $c.Body, $c.Glaze) }
if (-not $Quiet) {
    foreach ($c in $soft) { Write-Host ("grey only (shape tells them apart): {0} (body {1}, glaze {2})" -f $c.Pair, $c.Body, $c.Glaze) }
}
$verdict = if ($hard.Count -eq 0) { 'no colour collisions' } else { "$($hard.Count) colour collision(s)" }
$imgs = if ($PaletteOnly) { 'palette only' } else { "$written shot(s) simulated into $Out" }
Write-Host "colorblind: $verdict, $($soft.Count) greyscale-only pair(s); $imgs"
if ($FailOnCollision -and $hard.Count -gt 0) { exit 1 }
exit 0
