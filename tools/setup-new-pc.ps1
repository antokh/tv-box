# Install everything needed to build and install the TV box apps on a fresh Windows PC.
# Safe to re-run: each step skips what is already there. See docs\BUILDING.md.
#   powershell -ExecutionPolicy Bypass -File ".\tools\setup-new-pc.ps1"
param(
    [string]$Sdk = "$env:USERPROFILE\Android\Sdk"   # NOT under AppData (sandbox-virtualised)
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot

function Step($m) { Write-Host "`n== $m" -ForegroundColor Cyan }

Step "winget packages"
foreach ($id in "Git.Git", "Microsoft.OpenJDK.17", "Microsoft.OpenJDK.21", "Google.PlatformTools", "Python.Python.3.12") {
    $installed = winget list --id $id -e --source winget 2>$null | Select-String $id
    if ($installed) { "  $id already installed" }
    else { winget install --id $id -e --source winget --silent --accept-package-agreements --accept-source-agreements --disable-interactivity }
}

Step "Android command-line tools -> $Sdk"
$sdkm = "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkm)) {
    New-Item -ItemType Directory -Force $Sdk | Out-Null
    $zip = "$env:TEMP\cmdline-tools.zip"
    # If this link ever 404s, copy the current "Command line tools only" Windows link from
    # https://developer.android.com/studio#command-tools
    Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip
    $tmp = "$env:TEMP\cmdline-tools-x"; if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive $zip $tmp
    New-Item -ItemType Directory -Force "$Sdk\cmdline-tools" | Out-Null
    Move-Item "$tmp\cmdline-tools" "$Sdk\cmdline-tools\latest"
}

Step "SDK licences"
# sdkmanager ignores piped "y" on Windows, so accept by writing the licence hashes directly.
New-Item -ItemType Directory -Force "$Sdk\licenses" | Out-Null
Set-Content "$Sdk\licenses\android-sdk-license" -Encoding ascii -Value @(
    "", "8933bad161af4178b1185d1a37fbf41ea5269c55", "d56f5187479451eabf01fb78af6dfcb131a6481e", "24333f8a63b6825ea9c5514f83c2829b004d1fee")

Step "SDK packages"
$jdk17 = (Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" | Select -First 1).FullName
$env:JAVA_HOME = $jdk17; $env:Path = "$jdk17\bin;$env:Path"
& $sdkm "--sdk_root=$Sdk" "platform-tools" "platforms;android-34" "build-tools;34.0.0" `
    "platforms;android-36" "build-tools;36.0.0" "ndk;27.0.12077973" "cmake;3.22.1"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $Sdk, "User")

Step "auramusic\local.properties"
$lp = "$root\auramusic\local.properties"
$sdkEsc = $Sdk.Replace('\', '\\').Replace(':', '\:')
if (Test-Path $lp) {
    # Point sdk.dir at this PC's SDK, keep everything else.
    (Get-Content $lp) -replace '^sdk\.dir=.*', "sdk.dir=$sdkEsc" | Set-Content $lp -Encoding ascii
} else {
    Set-Content $lp -Encoding ascii -Value @(
        "sdk.dir=$sdkEsc",
        "release.storeFile=keystore/tvhome.keystore", "release.storePassword=YOUR_KEY_PASSWORD",
        "release.keyAlias=tvhome", "release.keyPassword=YOUR_KEY_PASSWORD",
        "debug.storeFile=keystore/tvhome.keystore", "debug.storePassword=YOUR_KEY_PASSWORD",
        "debug.keyAlias=tvhome", "debug.keyPassword=YOUR_KEY_PASSWORD",
        "# Installs next to the upstream app instead of replacing it.",
        "custom.appIdSuffix=.tvhome")
}
if (-not (Test-Path "$root\auramusic\app\keystore\tvhome.keystore")) {
    Write-Warning "auramusic\app\keystore\tvhome.keystore is missing! Restore it from backup before building."
}

Step "Done"
"Open a NEW terminal (so PATH/ANDROID_HOME refresh), then: adb connect 192.168.1.100:5555"
