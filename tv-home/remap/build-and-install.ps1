# Build the Remote Remap app (accessibility service that remaps remote buttons) and install + enable it.
#   .\remap\build-and-install.ps1              build + install + enable
#   .\remap\build-and-install.ps1 -BuildOnly   build only
#   .\remap\build-and-install.ps1 -Disable     turn the service off (button goes back to Xiaomi's handler)
param(
    [switch]$BuildOnly,
    [switch]$Disable,
    [string]$Device = "192.168.1.100:5555",
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" })
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$JDK = (Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" | Select -First 1).FullName + "\bin"
$env:JAVA_HOME = Split-Path $JDK; $env:Path = "$JDK;$env:Path"
$BT  = "$Sdk\build-tools\34.0.0"
$JAR = "$Sdk\platforms\android-34\android.jar"
$B   = "build"
$SVC = "com.mihir.remap/com.mihir.remap.RemapService"

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" }
function Run($exe, [string[]]$a) { & $exe @a; if ($LASTEXITCODE -ne 0) { throw "$exe failed ($LASTEXITCODE)" } }
function Shell([string]$c) { ((& $adb -s $Device shell $c) -join "`n").Trim() }

if ($Disable) {
    & $adb connect $Device | Out-Null
    $cur = (Shell "settings get secure enabled_accessibility_services") -split ':' | ? { $_ -and $_ -ne 'null' -and $_ -ne $SVC }
    if ($cur) { Shell "settings put secure enabled_accessibility_services $($cur -join ':')" | Out-Null }
    else { Shell "settings delete secure enabled_accessibility_services" | Out-Null }
    "Remote Remap disabled."; return
}

if (Test-Path $B) { Remove-Item -Recurse -Force $B }
New-Item -ItemType Directory -Force "$B\gen","$B\classes","$B\dex" | Out-Null
Run "$BT\aapt2.exe" @("compile", "--dir", "res", "-o", "$B\res.zip")
Run "$BT\aapt2.exe" @("link", "-o", "$B\base.apk", "-I", $JAR, "--manifest", "AndroidManifest.xml", "--java", "$B\gen",
    "--min-sdk-version", "30", "--target-sdk-version", "30", "$B\res.zip")
$srcs = Get-ChildItem src, "$B\gen" -Recurse -Filter *.java | % FullName
Run "$JDK\javac.exe" (@("-encoding", "UTF-8", "-nowarn", "-Xlint:-options", "--release", "11", "-classpath", $JAR, "-d", "$B\classes") + $srcs)
$classes = Get-ChildItem "$B\classes" -Recurse -Filter *.class | % FullName
Run "$BT\d8.bat" (@("--min-api", "30", "--lib", $JAR, "--output", "$B\dex") + $classes)
Copy-Item "$B\base.apk" "$B\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open((Resolve-Path "$B\unsigned.apk"), "Update")
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Resolve-Path "$B\dex\classes.dex"), "classes.dex") | Out-Null
$zip.Dispose()
Run "$BT\zipalign.exe" @("-f", "4", "$B\unsigned.apk", "$B\aligned.apk")
if (-not (Test-Path debug.keystore)) {
    Run "$JDK\keytool.exe" @("-genkeypair", "-keystore", "debug.keystore", "-storepass", "android", "-keypass", "android",
        "-alias", "remap", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=remap")
}
Run "$BT\apksigner.bat" @("sign", "--ks", "debug.keystore", "--ks-pass", "pass:android", "--out", "$B\remap.apk", "$B\aligned.apk")
Copy-Item "$B\remap.apk" "RemoteRemap.apk" -Force   # keep a copy you can reinstall from
"built RemoteRemap.apk ($((Get-Item "RemoteRemap.apk").Length) bytes)"
if ($BuildOnly) { return }

& $adb connect $Device | Out-Null
Run $adb @("-s", $Device, "install", "-r", "$B\remap.apk")
# Enable the accessibility service, keeping any others already enabled. Re-adding it restarts it with new config.
$cur = (Shell "settings get secure enabled_accessibility_services") -split ':' | ? { $_ -and $_ -ne 'null' -and $_ -ne $SVC }
Shell "settings put secure enabled_accessibility_services $((@($cur) + $SVC) -join ':')" | Out-Null
Shell "settings put secure accessibility_enabled 1" | Out-Null
"enabled: " + (Shell "settings get secure enabled_accessibility_services")
