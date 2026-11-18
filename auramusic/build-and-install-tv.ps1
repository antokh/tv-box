# Build the redesigned AuraMusic ("YouTube Music" on the TV box) and install it over adb.
#   .\auramusic\build-and-install-tv.ps1              build + install
#   .\auramusic\build-and-install-tv.ps1 -BuildOnly   build only
#   .\auramusic\build-and-install-tv.ps1 -InstallOnly install the last build
# Needs JDK 21, and local.properties with sdk.dir, the tvhome keystore and custom.appIdSuffix=.tvhome.
param(
    [switch]$BuildOnly,
    [switch]$InstallOnly,
    [string]$Device = "192.168.1.100:5555"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path local.properties)) { throw "local.properties is missing - see docs\BUILDING.md" }
if (-not (Test-Path app\keystore\tvhome.keystore)) { throw "app\keystore\tvhome.keystore is missing - restore it from backup; a new key can't update the installed app" }

$apk = "app\build\outputs\apk\fossTvArmeabi\release\app-foss-tv-armeabi-release.apk"

if (-not $InstallOnly) {
    $jdk = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue | Select -First 1
    if (-not $jdk) { throw "JDK 21 not found (winget install --id Microsoft.OpenJDK.21 -e --source winget)" }
    $env:JAVA_HOME = $jdk.FullName; $env:Path = "$($jdk.FullName)\bin;$env:Path"
    $t = Get-Date
    # Gradle prints warnings on stderr; don't let PowerShell treat them as failures.
    $ErrorActionPreference = "Continue"
    & .\gradlew.bat assembleFossTvArmeabiRelease --no-configuration-cache --console=plain 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($code -ne 0) { throw "Gradle build failed ($code)" }
    "Built in {0:N1} min: $apk" -f ((Get-Date) - $t).TotalMinutes
}
if ($BuildOnly) { return }

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" }
& $adb connect $Device | Out-Null
# Always an in-place update ("install -r"): keeps the app's data, so you stay signed in.
# NEVER uninstall or "pm clear" to get past an error - that signs her out and wipes her library.
$out = (& $adb -s $Device install -r $apk 2>&1) -join "`n"
$out
if ($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match") {
    throw "The TV refused the update: this APK is signed with a different key than the installed DodoMusic. Restore the original auramusic\app\keystore\tvhome.keystore (see docs\BUILDING.md). Do NOT uninstall the app - that would sign you out."
}
if ($out -notmatch "Success") { throw "adb install failed" }
"Updated com.auramusic.app.tvhome on $Device (data and sign-in kept)"
