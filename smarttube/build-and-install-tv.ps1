# Build the redesigned SmartTube ("DodoTube" on the TV box) and install it over adb.
#   .\smarttube\build-and-install-tv.ps1              build + install
#   .\smarttube\build-and-install-tv.ps1 -BuildOnly   build only
#   .\smarttube\build-and-install-tv.ps1 -InstallOnly install the last build
# Needs JDK 17, local.properties (sdk.dir) and keystore.properties (tvhome key + appIdSuffix=.tvhome).
param(
    [switch]$BuildOnly,
    [switch]$InstallOnly,
    [string]$Device = "192.168.1.100:5555"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path keystore.properties)) { throw "keystore.properties is missing - see docs\BUILDING.md" }
if (-not (Test-Path tvhome.keystore)) { throw "tvhome.keystore is missing - restore it from backup; a new key can't update the installed app" }

$apk = "smarttubetv\build\outputs\apk\ststable\release\SmartTube_stable_*_armeabi-v7a.apk"

if (-not $InstallOnly) {
    $jdk = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select -First 1
    if (-not $jdk) { throw "JDK 17 not found (winget install --id Microsoft.OpenJDK.17 -e --source winget)" }
    $env:JAVA_HOME = $jdk.FullName; $env:Path = "$($jdk.FullName)\bin;$env:Path"
    $t = Get-Date
    # Gradle prints warnings on stderr; don't let PowerShell treat them as failures.
    $ErrorActionPreference = "Continue"
    & .\gradlew.bat assembleStstableRelease -x lintVitalStstableRelease --console=plain 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($code -ne 0) { throw "Gradle build failed ($code)" }
    "Built in {0:N1} min" -f ((Get-Date) - $t).TotalMinutes
}
if ($BuildOnly) { return }

$file = (Get-ChildItem $apk | Sort-Object LastWriteTime | Select -Last 1).FullName
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" }
& $adb connect $Device | Out-Null
# Always an in-place update ("install -r"): keeps the app's data, so you stay signed in.
# NEVER uninstall or "pm clear" to get past an error - that signs her out and wipes settings.
$out = (& $adb -s $Device install -r $file 2>&1) -join "`n"
$out
if ($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match") {
    throw "The TV refused the update: this APK is signed with a different key than the installed DodoTube. Restore the original smarttube\tvhome.keystore (see docs\BUILDING.md). Do NOT uninstall the app - that would sign you out."
}
if ($out -notmatch "Success") { throw "adb install failed" }
"Updated org.smarttube.stable.tvhome on $Device (data and sign-in kept)"
