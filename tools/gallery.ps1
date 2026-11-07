<#
.SYNOPSIS
  Renders every screen state of the game to PNG files (the ScreenGallery test, drawn by Robolectric on the
  JVM, no emulator) at five sizes: phone, phone-land, small, tablet and tablet-port. Files are named
  <size>__<NN-state>.png. The folder is emptied first. Gradle output goes to build\logs\gallery.log.
.PARAMETER Out
  Output folder. Default: build\shots\gallery under the repository root.
.PARAMETER Size
  Render only these sizes (test method names: phone, phoneLandscape, smallPhone, tablet, tabletPortrait).
.PARAMETER Quiet
  Print only the verdict line.
.EXAMPLE
  .\tools\gallery.ps1 -Quiet
.EXAMPLE
  .\tools\gallery.ps1 -Quiet -Size phone,tablet
#>
param(
    [string]$Out = '',
    [ValidateSet('phone', 'phoneLandscape', 'smallPhone', 'tablet', 'tabletPortrait')][string[]]$Size = @(),
    [switch]$Quiet
)
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\env.ps1"
if (-not $Out) { $Out = Join-Path $root 'build\shots\gallery' }
$Out = [System.IO.Path]::GetFullPath($Out)
if (Test-Path $Out) { Get-ChildItem -Path $Out -Filter '*.png' | Remove-Item -Force }
New-Item -ItemType Directory -Force $Out | Out-Null
$logDir = Join-Path $root 'build\logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir 'gallery.log'
$init = Join-Path $PSScriptRoot 'gallery.gradle'
if ($Size.Count -gt 0) { $filter = ($Size | ForEach-Object { "--tests com.donuts.game.ScreenGallery.$_" }) -join ' ' }
else { $filter = '--tests com.donuts.game.ScreenGallery' }
Push-Location $root
try {
    $gradleOut = & cmd /c ".\gradlew.bat --init-script `"$init`" -Ddonuts.gallery.dir=`"$Out`" testDebugUnitTest $filter --console=plain 2>&1"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
[System.IO.File]::WriteAllLines($log, [string[]]@($gradleOut))
if (-not $Quiet) { Get-Content $log }
$count = @(Get-ChildItem -Path $Out -Filter '*.png').Count
if ($code -ne 0 -or $count -eq 0) {
    Select-String -Path $log -Pattern '^e: |What went wrong| FAILED$|Exception' -Context 0, 1 |
        Select-Object -First 30 | ForEach-Object { $_.Line }
    Write-Output "GALLERY FAILED (exit $code, $count files). Log: $log"
    exit 1
}
Write-Output "GALLERY OK: $count screens in $Out"
