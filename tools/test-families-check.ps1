<#
.SYNOPSIS
Tests tools\families-check.ps1 on fixture manifests and dependency reports in a scratch folder (no Gradle):
a clean release passes, and the advertising ID, network, location, an unknown permission, a debuggable
build, an ad SDK in the manifest or among the libraries (AndroidX's ad ID library too), an unreviewed
library, an empty report and missing or broken files all fail.

.PARAMETER Scratch
Scratch folder to use; deleted and recreated. Default build\test-families-check.

.EXAMPLE
.\tools\test-families-check.ps1
#>
param([string]$Scratch = 'build\test-families-check')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir = if ([System.IO.Path]::IsPathRooted($Scratch)) { $Scratch } else { Join-Path $root $Scratch }
$script = Join-Path $PSScriptRoot 'families-check.ps1'
$fails = 0
function Test-That([bool]$ok, [string]$what) {
    if ($ok) { Write-Host "ok   $what" } else { Write-Host "FAIL $what"; $script:fails++ }
}

if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
New-Item -ItemType Directory -Force -Path $dir | Out-Null

function New-Manifest([string]$name, [string]$permissions = '', [string]$components = '', [string]$appAttr = '') {
    $xml = @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.donuts.game"
    android:versionCode="37" android:versionName="2.0.5">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <permission android:name="com.donuts.game.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="signature" />
    <uses-permission android:name="com.donuts.game.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    $permissions
    <application android:label="@string/app_name" $appAttr>
        <activity android:name="com.donuts.game.MainActivity" android:exported="true" />
        <provider android:name="androidx.startup.InitializationProvider" android:exported="false">
            <meta-data android:name="androidx.profileinstaller.ProfileInstallerInitializer" android:value="androidx.startup" />
        </provider>
        $components
    </application>
</manifest>
"@
    $p = Join-Path $dir $name
    [System.IO.File]::WriteAllText($p, $xml)
    return $p
}

function New-Deps([string]$name, [string[]]$extra = @()) {
    $lines = @(
        '> Task :app:dependencies', '',
        'releaseRuntimeClasspath - Runtime classpath of ''/release''.',
        '+--- org.jetbrains.kotlin:kotlin-stdlib:2.2.10',
        '|    +--- org.jetbrains:annotations:13.0',
        '|    \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.2.10 (c)',
        '+--- androidx.core:core-ktx:1.12.0',
        '|    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0',
        '|    \--- com.google.guava:listenablefuture:1.0',
        '\--- androidx.activity:activity:1.8.0'
    ) + $extra + @('', '(c) - A dependency constraint, not a dependency.')
    $p = Join-Path $dir $name
    [System.IO.File]::WriteAllLines($p, $lines)
    return $p
}

function Invoke-Check([string]$manifest, [string]$deps) {
    $out = & $script -Manifest $manifest -Dependencies $deps -Quiet
    return @{ Code = $LASTEXITCODE; Text = ($out -join "`n") }
}

$clean = New-Manifest 'clean.xml'
$deps = New-Deps 'deps.txt'

$r = Invoke-Check $clean $deps
Test-That ($r.Code -eq 0 -and $r.Text -match '^FAMILIES OK') 'a clean release passes'
Test-That ($r.Text -match '6 libraries') 'counts the libraries, constraints left out'

$cases = @(
    @{ What = 'the advertising ID fails'; Match = 'AD_ID.*advertising ID'
       M = (New-Manifest 'adid.xml' '<uses-permission android:name="com.google.android.gms.permission.AD_ID" />') },
    @{ What = 'the Privacy Sandbox ad ID fails'; Match = 'ACCESS_ADSERVICES_AD_ID'
       M = (New-Manifest 'adservices.xml' '<uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" />') },
    @{ What = 'network access fails'; Match = 'INTERNET.*network'
       M = (New-Manifest 'internet.xml' '<uses-permission android:name="android.permission.INTERNET" />') },
    @{ What = 'location fails, sdk-23 form too'; Match = 'COARSE_LOCATION.*location'
       M = (New-Manifest 'location.xml' '<uses-permission-sdk-23 android:name="android.permission.ACCESS_COARSE_LOCATION" />') },
    @{ What = 'an unknown permission fails'; Match = 'CAMERA is not on the allowed list'
       M = (New-Manifest 'camera.xml' '<uses-permission android:name="android.permission.CAMERA" />') },
    @{ What = 'a debuggable build fails'; Match = 'debuggable'
       M = (New-Manifest 'debug.xml' '' '' 'android:debuggable="true"') },
    @{ What = 'an ad SDK activity in the manifest fails'; Match = 'activity com.google.android.gms.ads.AdActivity'
       M = (New-Manifest 'adactivity.xml' '' '<activity android:name="com.google.android.gms.ads.AdActivity" />') },
    @{ What = 'an ad SDK meta-data in the manifest fails'; Match = 'meta-data com.google.android.gms.ads.APPLICATION_ID'
       M = (New-Manifest 'admeta.xml' '' '<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="x" />') }
)
foreach ($c in $cases) {
    $r = Invoke-Check $c.M $deps
    Test-That ($r.Code -ne 0 -and $r.Text -match 'FAMILIES FAILED' -and $r.Text -match $c.Match) $c.What
}

$libCases = @(
    @{ What = 'a transitive ad SDK fails'; Match = 'com.google.android.gms:play-services-ads-identifier is an ad'
       Extra = @('     \--- com.google.android.gms:play-services-ads-identifier:18.0.0') },
    @{ What = 'the AndroidX ad ID library fails'; Match = 'androidx.ads:ads-identifier is an ad'
       Extra = @('\--- androidx.ads:ads-identifier:1.0.0-alpha05') },
    @{ What = 'an unreviewed library fails'; Match = 'com.squareup.okhttp3:okhttp has not been reviewed'
       Extra = @('\--- com.squareup.okhttp3:okhttp:4.12.0') }
)
$i = 0
foreach ($c in $libCases) {
    $i++
    $r = Invoke-Check $clean (New-Deps "deps$i.txt" $c.Extra)
    Test-That ($r.Code -ne 0 -and $r.Text -match $c.Match) $c.What
}

$empty = Join-Path $dir 'empty.txt'; [System.IO.File]::WriteAllText($empty, "BUILD SUCCESSFUL`r`n")
$r = Invoke-Check $clean $empty
Test-That ($r.Code -ne 0 -and $r.Text -match 'no libraries found') 'a report with no libraries fails'

$r = Invoke-Check (Join-Path $dir 'missing.xml') $deps
Test-That ($r.Code -ne 0 -and $r.Text -match 'manifest not found') 'a missing manifest fails'

$broken = Join-Path $dir 'broken.xml'; [System.IO.File]::WriteAllText($broken, '<manifest><application>')
$r = Invoke-Check $broken $deps
Test-That ($r.Code -ne 0 -and $r.Text -match 'not valid XML') 'a broken manifest fails'

Remove-Item -Recurse -Force $dir
if ($fails -gt 0) { Write-Host "test-families-check: $fails failure(s)"; exit 1 }
Write-Host 'test-families-check: all passed'
exit 0
