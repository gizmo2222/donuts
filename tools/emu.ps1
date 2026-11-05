<#
.SYNOPSIS
  Drives the Android emulator for on-screen checks: boot it, install and open the debug
  build, take a screenshot, shut everything down. App data on the emulator is kept.
.PARAMETER Action
  Start   - boot the AVD and wait until Android has finished booting.
  Install - install app\build\outputs\apk\debug\app-debug.apk over the old one and open the app.
  Shot    - save a screenshot to build\shots\<Name>.png.
  Drag    - press at the first point of -Path, move through the others, lift (draws a chain).
  Stop    - shut the emulator and the adb server down.
.PARAMETER Avd
  The AVD to boot (Start only). Default: Pixel_8a.
.PARAMETER Path
  Drag only: screen points in pixels, "x,y x,y x,y".
.PARAMETER ColdBoot
  Start only: boot from scratch instead of from the saved snapshot (use when the device stays "offline").
.PARAMETER Name
  File name (without .png) for the screenshot (Shot only). Default: shot.
.EXAMPLE
  .\tools\emu.ps1 Start; .\tools\emu.ps1 Install; .\tools\emu.ps1 Shot -Name before; .\tools\emu.ps1 Stop
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('Start', 'Install', 'Shot', 'Drag', 'Stop')]
    [string]$Action,
    [string]$Avd = 'Pixel_8a',
    [string]$Name = 'shot',
    [string]$Path = '',
    [switch]$ColdBoot
)
$root = Split-Path -Parent $PSScriptRoot
$sdk = $env:ANDROID_HOME
$props = Join-Path $root 'local.properties'
if (-not $sdk -and (Test-Path $props)) {
    $line = Select-String -Path $props -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
    if ($line) { $sdk = $line.Matches[0].Groups[1].Value -replace '\\\\', '\' -replace '\\:', ':' }
}
if (-not $sdk -or -not (Test-Path $sdk)) { Write-Output 'EMU FAILED: Android SDK not found (set ANDROID_HOME or sdk.dir)'; exit 1 }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'

function Test-Booted {
    $state = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    return $state -eq '1'
}

switch ($Action) {
    'Start' {
        & $adb start-server | Out-Null
        if (-not (Test-Booted)) {
            $emuArgs = @('-avd', $Avd, '-no-snapshot-save', '-no-boot-anim')
            if ($ColdBoot) { $emuArgs += '-no-snapshot-load' }
            Start-Process -FilePath $emulator -ArgumentList $emuArgs -WindowStyle Minimized
            $deadline = (Get-Date).AddMinutes(5)
            while (-not (Test-Booted)) {
                if ((Get-Date) -gt $deadline) { Write-Output "EMU FAILED: $Avd did not boot in 5 minutes"; exit 1 }
                Start-Sleep -Seconds 3
            }
        }
        Write-Output "EMU OK: $Avd is booted"
    }
    'Install' {
        $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
        if (-not (Test-Path $apk)) { Write-Output "EMU FAILED: $apk is missing (run .\tools\check.ps1 -Quiet first)"; exit 1 }
        $out = & $adb install -r $apk 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) { Write-Output $out.Trim(); Write-Output 'EMU FAILED: install'; exit 1 }
        & $adb shell am start -n com.donuts.game/.MainActivity | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Output 'EMU FAILED: could not open the app'; exit 1 }
        Write-Output 'EMU OK: debug build installed and opened'
    }
    'Shot' {
        $dir = Join-Path $root 'build\shots'
        New-Item -ItemType Directory -Force $dir | Out-Null
        $file = Join-Path $dir "$Name.png"
        & $adb shell screencap -p /sdcard/donuts-shot.png
        if ($LASTEXITCODE -ne 0) { Write-Output 'EMU FAILED: screencap'; exit 1 }
        & $adb pull /sdcard/donuts-shot.png $file | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Output 'EMU FAILED: could not copy the screenshot'; exit 1 }
        & $adb shell rm /sdcard/donuts-shot.png
        Write-Output "EMU OK: $file"
    }
    'Drag' {
        $points = @($Path.Trim() -split '\s+' | Where-Object { $_ -match '^\d+,\d+$' })
        if ($points.Count -lt 2) { Write-Output 'EMU FAILED: -Path needs at least two points, like "222,1134 96,1260"'; exit 1 }
        $steps = @()
        for ($i = 0; $i -lt $points.Count; $i++) {
            $xy = $points[$i] -replace ',', ' '
            $kind = if ($i -eq 0) { 'DOWN' } else { 'MOVE' }
            $steps += "input motionevent $kind $xy"
            if ($i -eq $points.Count - 1) { $steps += "input motionevent UP $xy" }
        }
        & $adb shell ($steps -join ' && sleep 0.1 && ')
        if ($LASTEXITCODE -ne 0) { Write-Output 'EMU FAILED: drag'; exit 1 }
        Write-Output "EMU OK: dragged through $($points.Count) points"
    }
    'Stop' {
        $devices = (& $adb devices 2>$null | Out-String)
        if ($devices -match 'emulator-\d+') {
            & $adb emu kill 2>$null | Out-Null
            $deadline = (Get-Date).AddSeconds(30)
            while ((Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
                Start-Sleep -Seconds 1
            }
        }
        # An emulator that adb cannot reach ("offline") ignores "emu kill": end the process instead
        Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
        & $adb kill-server 2>$null | Out-Null
        $left = @(Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue).Count
        if ($left -gt 0) { Write-Output 'EMU FAILED: the emulator is still running'; exit 1 }
        Write-Output 'EMU OK: emulator and adb server are stopped'
    }
}
exit 0
