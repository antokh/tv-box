# Windows port of build-and-install.sh: build the launcher APK with bare SDK tools and install it over adb.
#   .\launcher\build-and-install.ps1                 build + install
#   .\launcher\build-and-install.ps1 -BuildOnly      build only
param(
    [switch]$BuildOnly,
    [string]$Device = "192.168.1.100:5555",
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$StockLauncher = "com.google.android.apps.tv.launcherx"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$JDK = (Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" | Select -First 1).FullName + "\bin"
$env:JAVA_HOME = Split-Path $JDK; $env:Path = "$JDK;$env:Path"
$BT  = "$Sdk\build-tools\34.0.0"
$JAR = "$Sdk\platforms\android-34\android.jar"
$B   = "build"

function Run($exe, [string[]]$a) { & $exe @a; if ($LASTEXITCODE -ne 0) { throw "$exe failed ($LASTEXITCODE)" } }

if (Test-Path $B) { Remove-Item -Recurse -Force $B }
New-Item -ItemType Directory -Force "$B\gen","$B\classes","$B\dex" | Out-Null
Run "$BT\aapt2.exe" @("compile", "--dir", "res", "-o", "$B\res.zip")
Run "$BT\aapt2.exe" @("link", "-o", "$B\base.apk", "-I", $JAR, "--manifest", "AndroidManifest.xml", "--java", "$B\gen",
    "--min-sdk-version", "30", "--target-sdk-version", "30", "$B\res.zip")
$srcs = Get-ChildItem src, "$B\gen" -Recurse -Filter *.java | % FullName
Run "$JDK\javac.exe" (@("-encoding", "UTF-8", "-nowarn", "-Xlint:-options", "--release", "11", "-classpath", $JAR, "-d", "$B\classes") + $srcs)
$classes = Get-ChildItem "$B\classes" -Recurse -Filter *.class | % FullName
Run "$BT\d8.bat" (@("--min-api", "30", "--lib", $JAR, "--output", "$B\dex") + $classes)

# Add classes.dex to the APK (stored at the zip root).
Copy-Item "$B\base.apk" "$B\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open((Resolve-Path "$B\unsigned.apk"), "Update")
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Resolve-Path "$B\dex\classes.dex"), "classes.dex") | Out-Null
# Assets are added here, not via aapt2 -A, because aapt2 on Windows writes backslashes into entry names.
$assetsRoot = (Resolve-Path assets).Path
foreach ($f in Get-ChildItem assets -Recurse -File) {
    $entry = "assets/" + $f.FullName.Substring($assetsRoot.Length + 1).Replace('\', '/')
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $entry, [IO.Compression.CompressionLevel]::NoCompression) | Out-Null
}
$zip.Dispose()

Run "$BT\zipalign.exe" @("-f", "4", "$B\unsigned.apk", "$B\aligned.apk")
if (-not (Test-Path debug.keystore)) {
    Run "$JDK\keytool.exe" @("-genkeypair", "-keystore", "debug.keystore", "-storepass", "android", "-keypass", "android",
        "-alias", "tvhome", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=tvhome")
}
$env:JAVA_HOME = Split-Path $JDK
Run "$BT\apksigner.bat" @("sign", "--ks", "debug.keystore", "--ks-pass", "pass:android", "--out", "$B\tvhome.apk", "$B\aligned.apk")
"built $B\tvhome.apk ($((Get-Item "$B\tvhome.apk").Length) bytes)"
if ($BuildOnly) { return }

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" }
& $adb connect $Device | Out-Null
Run $adb @("-s", $Device, "install", "-r", "$B\tvhome.apk")
# One command per shell call: chained pm commands can silently stop after the first.
$cmds = @(
    "pm grant com.mihir.tvhome android.permission.ACCESS_FINE_LOCATION",
    "cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity",
    "pm disable-user --user 0 $StockLauncher",
    "pm disable-user --user 0 com.google.android.tungsten.setupwraith",
    "cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity",
    "am force-stop com.android.tv.settings",
    "input keyevent KEYCODE_HOME"
)
foreach ($c in $cmds) { "> $c"; & $adb -s $Device shell $c }
"Installed. Press Home on the remote."
