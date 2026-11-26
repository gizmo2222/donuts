<#
.SYNOPSIS
Tests tools\env.ps1 on scratch checkouts (no Gradle, no SDK needed): sdk.dir is read with its escapes undone,
ANDROID_HOME wins when set, a git worktree with no local.properties takes the main checkout's sdk.dir into
ANDROID_HOME, a folder with neither gives an empty $envSdk, and a JAVA_HOME already set is kept.

.PARAMETER Scratch
Scratch folder to use; deleted and recreated. Default: donuts-test-env in the user's temp folder. It must not
sit inside another git checkout, or the "no checkout" case would find that checkout's local.properties.

.EXAMPLE
.\tools\test-env.ps1
#>
param([string]$Scratch = (Join-Path ([System.IO.Path]::GetTempPath()) 'donuts-test-env'))

$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'env.ps1'
$fails = 0
function Test-That([bool]$ok, [string]$what) {
    if ($ok) { Write-Host "ok   $what" } else { Write-Host "FAIL $what"; $script:fails++ }
}

if (Test-Path $Scratch) { Remove-Item -Recurse -Force $Scratch }
New-Item -ItemType Directory -Force -Path $Scratch | Out-Null

function New-Checkout([string]$name, [string]$props) {
    # A folder with tools\env.ps1 copied in and, when $props is given, a local.properties holding it.
    $dir = Join-Path $Scratch $name
    New-Item -ItemType Directory -Force -Path (Join-Path $dir 'tools') | Out-Null
    Copy-Item $source (Join-Path $dir 'tools\env.ps1')
    if ($props) { [System.IO.File]::WriteAllText((Join-Path $dir 'local.properties'), $props) }
    return $dir
}

function Invoke-Env([string]$dir, [string]$androidHome, [string]$javaHome) {
    # Dot-sources the copy in $dir with the given starting variables; returns what it left behind.
    $saved = @{ A = $env:ANDROID_HOME; J = $env:JAVA_HOME }
    try {
        $env:ANDROID_HOME = $androidHome
        $env:JAVA_HOME = $javaHome
        $sdk = & { . (Join-Path $dir 'tools\env.ps1'); $envSdk }
        return @{ Sdk = [string]$sdk; AndroidHome = [string]$env:ANDROID_HOME; JavaHome = [string]$env:JAVA_HOME }
    } finally {
        $env:ANDROID_HOME = $saved.A
        $env:JAVA_HOME = $saved.J
    }
}

# 1. This checkout's local.properties, escaped as Android Studio writes it
$plain = New-Checkout 'plain' "## comment`nsdk.dir=C\:\\Android\\Sdk`n"
$r = Invoke-Env $plain '' 'C:\SomeJdk'
Test-That ($r.Sdk -eq 'C:\Android\Sdk') "sdk.dir is read with its escapes undone (got '$($r.Sdk)')"
Test-That ($r.AndroidHome -eq '') 'ANDROID_HOME is left unset when local.properties has the SDK'
Test-That ($r.JavaHome -eq 'C:\SomeJdk') 'a JAVA_HOME already set is kept'

# 2. ANDROID_HOME wins over local.properties
$r = Invoke-Env $plain 'D:\Other\Sdk' 'C:\SomeJdk'
Test-That ($r.Sdk -eq 'D:\Other\Sdk') 'ANDROID_HOME wins over sdk.dir'
Test-That ($r.AndroidHome -eq 'D:\Other\Sdk') 'ANDROID_HOME is not changed'

# 3. local.properties without sdk.dir, no ANDROID_HOME
$nosdk = New-Checkout 'nosdk' "KEYSTORE_PATH=x`n"
$r = Invoke-Env $nosdk '' 'C:\SomeJdk'
Test-That ($r.Sdk -eq '') 'a local.properties with no sdk.dir gives no SDK'

# 4. No local.properties and not a git checkout
$bare = New-Checkout 'bare' ''
$r = Invoke-Env $bare '' 'C:\SomeJdk'
Test-That ($r.Sdk -eq '' -and $r.AndroidHome -eq '') 'no local.properties outside git gives no SDK'

# 5. A git worktree with no local.properties takes the main checkout's sdk.dir
$main = New-Checkout 'main' ''
$git = @('-c', 'core.hooksPath=.no-hooks', '-c', 'user.name=test', '-c', 'user.email=test@example.invalid')
& git init -q --template= $main
[System.IO.File]::WriteAllText((Join-Path $main 'readme.txt'), 'x')
& git -C $main @git add readme.txt
& git -C $main @git commit -q -m 'scratch'
[System.IO.File]::WriteAllText((Join-Path $main 'local.properties'), "sdk.dir=E\:\\Main\\Sdk`n")
$wt = Join-Path $Scratch 'worktree'
& git -C $main @git worktree add -q $wt 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host 'FAIL git worktree add'; exit 1 }
New-Item -ItemType Directory -Force -Path (Join-Path $wt 'tools') | Out-Null
Copy-Item $source (Join-Path $wt 'tools\env.ps1')
$r = Invoke-Env $wt '' 'C:\SomeJdk'
Test-That ($r.AndroidHome -eq 'E:\Main\Sdk') "a worktree sets ANDROID_HOME from the main checkout (got '$($r.AndroidHome)')"
Test-That ($r.Sdk -eq 'E:\Main\Sdk') 'a worktree gets the main checkout SDK as $envSdk'

Remove-Item -Recurse -Force $Scratch
if ($fails -gt 0) { Write-Host "test-env: $fails failure(s)"; exit 1 }
Write-Host 'test-env: all passed'
