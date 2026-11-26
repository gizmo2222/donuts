<#
.SYNOPSIS
  Dot-sourced by the build scripts: sets JAVA_HOME and ANDROID_HOME when they are missing, and $envSdk to the
  Android SDK folder. JAVA_HOME falls back to the JDK that ships with Android Studio. ANDROID_HOME is set only
  when the checkout has no local.properties (a git worktree); it is read from the main checkout's
  local.properties. $envSdk is ANDROID_HOME, else this checkout's sdk.dir, else the main checkout's sdk.dir,
  else empty; emu.ps1 uses it to find adb and the emulator.
.EXAMPLE
  . "$PSScriptRoot\env.ps1"; if (-not $envSdk) { throw 'Android SDK not found' }
#>
$envRoot = Split-Path -Parent $PSScriptRoot
function Get-EnvSdkDir([string]$props) {
    # The sdk.dir value of a local.properties file, with its properties escapes (\\, \:) undone; '' if none.
    if (-not (Test-Path $props)) { return '' }
    $line = Select-String -Path $props -Pattern '^sdk\.dir=' | Select-Object -First 1
    if (-not $line) { return '' }
    return ($line.Line -replace '^sdk\.dir=', '') -replace '\\(.)', '$1'
}
if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
$envSdk = Get-EnvSdkDir (Join-Path $envRoot 'local.properties')
if (-not $env:ANDROID_HOME -and -not (Test-Path (Join-Path $envRoot 'local.properties'))) {
    # Its own error preference, so a caller's 'Stop' does not turn "not a git repository" into a throw.
    $common = & { $ErrorActionPreference = 'SilentlyContinue'; git -C $envRoot rev-parse --path-format=absolute --git-common-dir 2>$null }
    if ($common) {
        $mainSdk = Get-EnvSdkDir (Join-Path (Split-Path -Parent $common) 'local.properties')
        if ($mainSdk) { $env:ANDROID_HOME = $mainSdk }
    }
}
if ($env:ANDROID_HOME) { $envSdk = $env:ANDROID_HOME }
