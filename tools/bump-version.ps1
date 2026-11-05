<#
.SYNOPSIS
Sets the app version in app\build.gradle for the next Google Play upload.

.DESCRIPTION
Writes versionName and versionCode into app\build.gradle. versionCode goes up by one unless -Code is given.
Stops with a non-zero exit code if the new code is not higher than the current one, because Google Play
refuses an upload whose versionCode it has already seen. Running it again with the same -Name and -Code
changes nothing.

.PARAMETER Name
The new versionName, for example 2.0.3.

.PARAMETER Code
The new versionCode. Defaults to the current versionCode plus one.

.PARAMETER WhatIf
Prints the change without writing the file.

.EXAMPLE
.\tools\bump-version.ps1 -Name 2.0.3
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Name,
    [int]$Code = 0
)

$ErrorActionPreference = 'Stop'
$path = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\build.gradle'
$text = [System.IO.File]::ReadAllText($path)

$codeMatch = [regex]::Match($text, '(?m)^(\s*versionCode\s+)(\d+)')
$nameMatch = [regex]::Match($text, '(?m)^(\s*versionName\s+)"([^"]+)"')
if (-not $codeMatch.Success -or -not $nameMatch.Success) {
    Write-Error "versionCode or versionName not found in $path"
    exit 1
}
$oldCode = [int]$codeMatch.Groups[2].Value
$oldName = $nameMatch.Groups[2].Value
if ($Code -eq 0) {
    # No -Code given: a name that is already set means this bump has been done.
    if ($Name -eq $oldName) { $Code = $oldCode } else { $Code = $oldCode + 1 }
}

if ($Code -eq $oldCode -and $Name -eq $oldName) {
    "Already at $Name (versionCode $Code). Nothing to change."
    exit 0
}
if ($Code -le $oldCode) {
    Write-Error "versionCode $Code is not higher than the current $oldCode. Google Play would refuse the upload."
    exit 1
}

$new = [regex]::Replace($text, '(?m)^(\s*versionCode\s+)\d+', "`${1}$Code")
$new = [regex]::Replace($new, '(?m)^(\s*versionName\s+)"[^"]+"', "`${1}`"$Name`"")

if ($PSCmdlet.ShouldProcess($path, "Set version $oldName ($oldCode) -> $Name ($Code)")) {
    [System.IO.File]::WriteAllText($path, $new, (New-Object System.Text.UTF8Encoding($false)))
    "Version: $oldName ($oldCode) -> $Name ($Code)"
}
exit 0
