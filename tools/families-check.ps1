<#
.SYNOPSIS
Families-policy release check. Reads the release build's merged manifest and its runtime libraries and
fails when anything breaks the Play Families rules or the privacy promise: the advertising ID, network,
location or any permission past vibrate; an ad, analytics, tracking or purchase SDK (in the manifest or
among the libraries, transitive ones included); a library nobody has reviewed; a debuggable build.
It changes nothing. Unless both -Manifest and -Dependencies are given it runs Gradle
(processReleaseManifest and the releaseRuntimeClasspath dependency report; no signing needed), with the
output in build\logs\families.log.

.PARAMETER Manifest
A merged AndroidManifest.xml to check instead of building the release one.

.PARAMETER Dependencies
A saved `gradlew :app:dependencies --configuration releaseRuntimeClasspath` report to check instead of
running Gradle.

.PARAMETER Quiet
Print only the verdict line and the problems.

.EXAMPLE
.\tools\families-check.ps1 -Quiet

.EXAMPLE
.\tools\families-check.ps1 -Manifest .\my\AndroidManifest.xml -Dependencies .\my\deps.txt
#>
param([string]$Manifest, [string]$Dependencies, [switch]$Quiet)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$problems = New-Object System.Collections.Generic.List[string]
function Add-Problem([string]$text) { $script:problems.Add($text) }
function Stop-Failed([string]$why) { Write-Output "FAMILIES FAILED: $why"; exit 1 }
function Resolve-Input([string]$p) { if ([System.IO.Path]::IsPathRooted($p)) { $p } else { Join-Path (Get-Location) $p } }

# Permissions the app may ask for: vibrate, and the signature permission androidx.core adds for its own
# receivers (it grants nothing). Anything else needs docs\privacy-policy.md and the Data safety answers
# rewritten first, then this list.
$allowedPermissions = @('android.permission.VIBRATE', '{pkg}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')
$permissionReasons = [ordered]@{
    'com.google.android.gms.permission.AD_ID'          = 'the advertising ID (Families: never in a child-only app targeting API 33+)'
    'android.permission.ACCESS_ADSERVICES_AD_ID'       = 'the Privacy Sandbox advertising ID (Families: no ad identifiers)'
    'android.permission.ACCESS_ADSERVICES_ATTRIBUTION' = 'Privacy Sandbox ad attribution (Families: no ad tracking)'
    'android.permission.ACCESS_ADSERVICES_TOPICS'      = 'Privacy Sandbox ad topics (Families: no ad tracking)'
    'android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE' = 'Privacy Sandbox ad audiences (Families: no ad tracking)'
    'android.permission.INTERNET'                      = 'network access (the privacy promise says none)'
    'android.permission.ACCESS_NETWORK_STATE'          = 'network state (the privacy promise says no network)'
    'android.permission.ACCESS_WIFI_STATE'             = 'wifi state (the privacy promise says no network)'
    'android.permission.ACCESS_FINE_LOCATION'          = 'precise location (Families: never)'
    'android.permission.ACCESS_COARSE_LOCATION'        = 'approximate location (Families: never)'
    'android.permission.ACCESS_BACKGROUND_LOCATION'    = 'background location (Families: never)'
    'com.android.vending.BILLING'                      = 'in-app purchases (the promise says none)'
}

# Ad, analytics, crash-reporting, attribution and purchase SDKs, by package or Maven group prefix.
$deniedPrefixes = @(
    'com.google.android.gms', 'com.google.firebase', 'com.google.ads', 'com.google.android.ads',
    'com.android.billingclient', 'androidx.ads', 'androidx.privacysandbox',
    'com.facebook', 'com.applovin', 'com.unity3d', 'com.ironsource', 'com.appsflyer', 'com.adjust',
    'com.flurry', 'com.chartboost', 'com.vungle', 'com.mopub', 'com.inmobi', 'com.bytedance', 'com.pangle',
    'com.tapjoy', 'com.onesignal', 'io.branch', 'com.amplitude', 'com.mixpanel', 'com.segment',
    'io.sentry', 'com.bugsnag', 'com.crashlytics', 'com.instabug', 'com.kochava', 'com.startapp',
    'com.yandex', 'com.appodeal', 'com.fyber', 'com.mbridge', 'com.my.target', 'com.huawei.hms'
)
# Libraries reviewed as sending nothing: AndroidX (minus the ad ones above), Kotlin, and the one Guava stub.
$allowedGroups = @('org.jetbrains', 'org.jetbrains.kotlin', 'org.jetbrains.kotlinx', 'org.jspecify')
$allowedModules = @('com.google.guava:listenablefuture')

function Get-Denied([string]$name) {
    foreach ($d in $deniedPrefixes) { if ($name -eq $d -or $name.StartsWith("$d.") -or $name.StartsWith("${d}:")) { return $d } }
    return $null
}

# 1. Inputs: build what was not given.
$log = $null
if (-not $Manifest -or -not $Dependencies) {
    . (Join-Path $PSScriptRoot 'env.ps1')
    $tasks = @()
    if (-not $Manifest) { $tasks += ':app:processReleaseManifest' }
    if (-not $Dependencies) { $tasks += ':app:dependencies --configuration releaseRuntimeClasspath' }
    $logDir = Join-Path $root 'build\logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $log = Join-Path $logDir 'families.log'
    Push-Location $root
    try { $out = & cmd /c ".\gradlew.bat $($tasks -join ' ') --console=plain 2>&1"; $code = $LASTEXITCODE }
    finally { Pop-Location }
    [System.IO.File]::WriteAllText($log, ($out -join "`r`n"))
    if ($code -ne 0) { Stop-Failed "Gradle failed (exit $code). Log: $log" }
    if (-not $Dependencies) { $Dependencies = $log }
    if (-not $Manifest) {
        $inter = Join-Path $root 'app\build\intermediates'
        $found = @(Get-ChildItem -Path (Join-Path $inter 'merged_manifests\release') -Recurse -Filter AndroidManifest.xml -ErrorAction SilentlyContinue) +
                 @(Get-ChildItem -Path (Join-Path $inter 'merged_manifest\release') -Recurse -Filter AndroidManifest.xml -ErrorAction SilentlyContinue)
        if ($found.Count -eq 0) { Stop-Failed "no release merged manifest under $inter. Log: $log" }
        $Manifest = $found[0].FullName
    }
} else {
    $Manifest = Resolve-Input $Manifest
    $Dependencies = Resolve-Input $Dependencies
}
if (-not (Test-Path -LiteralPath $Manifest)) { Stop-Failed "manifest not found: $Manifest" }
if (-not (Test-Path -LiteralPath $Dependencies)) { Stop-Failed "dependency report not found: $Dependencies" }

# 2. The merged manifest.
$ns = 'http://schemas.android.com/apk/res/android'
try { $doc = New-Object System.Xml.XmlDocument; $doc.Load($Manifest) }
catch { Stop-Failed "manifest is not valid XML: $Manifest ($($_.Exception.Message))" }
$top = $doc.DocumentElement
if (-not $top -or $top.LocalName -ne 'manifest') { Stop-Failed "not an Android manifest: $Manifest" }
$pkg = $top.GetAttribute('package')
if (-not $pkg) {
    $m = [regex]::Match((Get-Content -Raw (Join-Path $root 'app\build.gradle')), 'applicationId\s+[''"]([^''"]+)')
    $pkg = $m.Groups[1].Value
}
$allowed = $allowedPermissions | ForEach-Object { $_.Replace('{pkg}', $pkg) }

$permissions = @()
foreach ($node in $top.ChildNodes) {
    if ($node.NodeType -ne 'Element') { continue }
    if ($node.LocalName -ne 'uses-permission' -and $node.LocalName -ne 'uses-permission-sdk-23') { continue }
    $name = $node.GetAttribute('name', $ns)
    $permissions += $name
    if ($allowed -contains $name) { continue }
    if ($permissionReasons.Contains($name)) { Add-Problem "permission $name asks for $($permissionReasons[$name])" }
    else { Add-Problem "permission $name is not on the allowed list (vibrate only); rewrite docs\privacy-policy.md and the Data safety answers first, then this script's list" }
}

$target = $null
foreach ($node in $top.ChildNodes) {
    if ($node.NodeType -eq 'Element' -and $node.LocalName -eq 'uses-sdk') { $target = $node.GetAttribute('targetSdkVersion', $ns) }
}
if (-not $target) { Add-Problem 'the manifest has no targetSdkVersion (is this the merged manifest?)' }

$app = $null
foreach ($node in $top.ChildNodes) { if ($node.NodeType -eq 'Element' -and $node.LocalName -eq 'application') { $app = $node } }
if (-not $app) { Add-Problem 'the manifest has no <application>' }
else {
    if ($app.GetAttribute('debuggable', $ns) -eq 'true') { Add-Problem 'the application is debuggable; check the release build, not the debug one' }
    foreach ($el in $app.SelectNodes('.//*')) {
        $name = $el.GetAttribute('name', $ns)
        if (-not $name) { continue }
        $d = Get-Denied $name
        if ($d) { Add-Problem "manifest $($el.LocalName) $name comes from an ad, analytics, tracking or purchase SDK ($d)" }
    }
}

# 3. The runtime libraries, transitive ones included. Constraint lines (c) add no library.
$modules = New-Object System.Collections.Generic.SortedSet[string]
foreach ($line in [System.IO.File]::ReadAllLines($Dependencies)) {
    if ($line -match '\(c\)\s*$') { continue }
    $m = [regex]::Match($line, '^[\s|+\\]*[+\\]--- ([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?=[:\s]|$)')
    if ($m.Success) { [void]$modules.Add("$($m.Groups[1].Value):$($m.Groups[2].Value)") }
}
if ($modules.Count -eq 0) { Add-Problem "no libraries found in the dependency report $Dependencies" }
foreach ($mod in $modules) {
    $group = $mod.Split(':')[0]
    $d = Get-Denied $mod
    if ($d) { Add-Problem "library $mod is an ad, analytics, tracking or purchase SDK ($d)"; continue }
    if ($group -eq 'androidx' -or $group.StartsWith('androidx.')) { continue }
    if ($allowedGroups -contains $group -or $allowedModules -contains $mod) { continue }
    Add-Problem "library $mod has not been reviewed; check that it sends nothing, then add it to this script's allowed list"
}

# 4. Verdict.
if (-not $Quiet) {
    Write-Output "Manifest:     $Manifest"
    Write-Output "Dependencies: $Dependencies"
    Write-Output "targetSdk:    $target"
    Write-Output "Permissions:  $(if ($permissions) { $permissions -join ', ' } else { 'none' })"
    Write-Output "Libraries:    $($modules.Count)"
    foreach ($mod in $modules) { Write-Output "  $mod" }
}
if ($problems.Count -gt 0) {
    Write-Output "FAMILIES FAILED: $($problems.Count) problem(s)"
    foreach ($p in $problems) { Write-Output "  - $p" }
    exit 1
}
Write-Output "FAMILIES OK: targetSdk $target, permissions $($permissions.Count) (all allowed), $($modules.Count) libraries (all reviewed, no ad or tracking SDK)"
exit 0
