# Builds the signed Google Play bundle for Donuts for Steven.
# Run from the project root in PowerShell:   .\release.ps1
# Output: app\build\outputs\bundle\release\app-release.aab

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# 1. Java: use Android Studio's bundled JDK unless JAVA_HOME is already set
if (-not $env:JAVA_HOME) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (-not (Test-Path "$jbr\bin\java.exe")) { throw "JAVA_HOME is not set and Android Studio's JDK was not found at $jbr" }
    $env:JAVA_HOME = $jbr
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
"Java: $env:JAVA_HOME"

# 2. Signing credentials must be present in local.properties (never committed)
$props = @{}
Get-Content local.properties | ForEach-Object { if ($_ -match '^\s*([^#!][^=]*)=(.*)$') { $props[$matches[1].Trim()] = $matches[2] } }
foreach ($k in 'KEYSTORE_PATH','KEYSTORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD') {
    if (-not $props.ContainsKey($k) -or [string]::IsNullOrWhiteSpace($props[$k])) { throw "local.properties is missing $k (see README, Release checklist)" }
}
$ksPath = $props['KEYSTORE_PATH'] -replace '\\(.)', '$1'
if (-not (Test-Path $ksPath)) { throw "KEYSTORE_PATH in local.properties points to a file that does not exist: $ksPath" }
"Keystore: $ksPath  (alias $($props['KEY_ALIAS']))"

# 3. Version being built
$gradle = Get-Content app\build.gradle -Raw
$code = [regex]::Match($gradle, 'versionCode\s+(\d+)').Groups[1].Value
$name = [regex]::Match($gradle, 'versionName\s+"([^"]+)"').Groups[1].Value
"Version: $name (versionCode $code)"

# 4. Build. Gradle's own output is shown; any failure stops here with a non-zero exit code.
""
.\gradlew.bat bundleRelease --console=plain
if ($LASTEXITCODE -ne 0) {
    ""
    Write-Host "BUILD FAILED. Nothing was produced. Read the 'What went wrong' section above." -ForegroundColor Red
    Write-Host "If it says 'keystore password was incorrect', fix KEYSTORE_PASSWORD / KEY_PASSWORD in local.properties." -ForegroundColor Red
    exit 1
}

# 5. Result
$aab = Get-Item app\build\outputs\bundle\release\app-release.aab
""
Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
"Bundle: $($aab.FullName)"
"Size:   $([math]::Round($aab.Length / 1MB, 2)) MB"
"Built:  $($aab.LastWriteTime)"
""
"Next: .\gradlew.bat publishReleaseBundle uploads it to closed testing (see README, Upload to Google Play)"
