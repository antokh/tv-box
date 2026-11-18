# Windows version of tools/disable.sh and tools/restore.sh.
#   tools\debloat.ps1 <list.txt>            disable every package in the list
#   tools\debloat.ps1 <list.txt> -Restore   re-enable them
param(
    [Parameter(Mandatory)][string]$List,
    [switch]$Restore,
    [string]$Device = "192.168.1.100:5555"
)
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" }
& $adb connect $Device | Out-Null
$pkgs = Get-Content $List | % { ($_ -replace '#.*$', '').Trim() } | ? { $_ }
# One command per shell call: chained pm commands can silently stop after the first.
foreach ($p in $pkgs) {
    $cmd = if ($Restore) { "pm enable $p" } else { "pm disable-user --user 0 $p" }
    $r = (& $adb -s $Device shell $cmd 2>&1) -join ' '
    "{0,-46} {1}" -f $p, $r
}
