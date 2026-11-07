<#
.SYNOPSIS
  Dot-sourced by the build scripts: sets JAVA_HOME and ANDROID_HOME when they are missing.
  JAVA_HOME falls back to the JDK that ships with Android Studio. ANDROID_HOME is needed only when the
  checkout has no local.properties (a git worktree); it is read from the main checkout's local.properties.
.EXAMPLE
  . "$PSScriptRoot\env.ps1"
#>
$envRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
if (-not $env:ANDROID_HOME -and -not (Test-Path (Join-Path $envRoot 'local.properties'))) {
    $common = & git -C $envRoot rev-parse --path-format=absolute --git-common-dir 2>$null
    if ($common) {
        $mainProps = Join-Path (Split-Path -Parent $common) 'local.properties'
        if (Test-Path $mainProps) {
            $line = Select-String -Path $mainProps -Pattern '^sdk\.dir=' | Select-Object -First 1
            if ($line) {
                $env:ANDROID_HOME = ($line.Line -replace '^sdk\.dir=', '') -replace '\\(.)', '$1'
            }
        }
    }
}
