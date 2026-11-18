# Undo build-and-install.ps1: re-enable the Google TV home screen and remove tv-home.
param([string]$Device = "192.168.1.100:5555")
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" }
& $adb connect $Device | Out-Null
foreach ($c in @(
    "pm enable com.google.android.apps.tv.launcherx",
    "pm enable com.google.android.tungsten.setupwraith",
    "cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity",
    "pm uninstall com.mihir.tvhome",
    "input keyevent KEYCODE_HOME")) { "> $c"; & $adb -s $Device shell $c }
