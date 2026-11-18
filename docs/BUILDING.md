# Building and installing

Everything builds on **macOS** (bash scripts) or **Windows** (PowerShell scripts) and installs to the
TV box over Wi-Fi ADB. No Android Studio needed.

## 1. Prepare the TV box

1. **Settings → Device Preferences → About** → press OK on **Build** 7 times.
2. **Developer options** → turn on **USB debugging** (and **Network debugging** if your box has it).
3. Note the box's IP address (Settings → Network). The scripts use `192.168.1.100:5555` by default:
   - Mac: `DEVICE=<ip>:5555 bash <script>` or edit `DEVICE` in `tools/mac-env.sh`
   - Windows: `-Device <ip>:5555`
4. The first `adb connect` shows **Allow USB debugging?** on the TV: tick *Always allow* and press **OK**.

## 2. Toolchain

| | Mac | Windows |
|---|---|---|
| Set-up script | `bash tools/setup-new-mac.sh` | `powershell -ExecutionPolicy Bypass -File tools\setup-new-pc.ps1` |
| Installs | JDK 17 + 21, Android SDK (platform-tools, build-tools 30/34/36, platforms 34/36, NDK 27, CMake 3.22) | same, via winget + sdkmanager |

The Mac script puts the whole toolchain in a `tv-box-toolchain/` folder **next to** the project
(handy on an external drive) and needs neither Homebrew nor sudo.

## 3. Signing keys (yours, not in this repo)

Keys are deliberately **not** committed. Create your own once and keep a backup: Android only updates an
app in place when it is signed with the same key, so losing the key means uninstalling (and signing in
again).

```bash
keytool -genkeypair -v -keystore tvhome.keystore -alias tvhome -keyalg RSA -keysize 2048 -validity 10000
```

| App | Key file | Config file (not committed) |
|---|---|---|
| DodoTube | `smarttube/tvhome.keystore` | `smarttube/keystore.properties` |
| DodoMusic | `auramusic/app/keystore/tvhome.keystore` | `auramusic/local.properties` |
| Launcher, Remote Remap | `tv-home/launcher/debug.keystore`, `tv-home/remap/debug.keystore` | none (password `android`, standard debug key) |

`smarttube/keystore.properties`:

```properties
storeFile=../tvhome.keystore
storePassword=YOUR_KEY_PASSWORD
keyAlias=tvhome
keyPassword=YOUR_KEY_PASSWORD
# Installs next to the official SmartTube instead of replacing it.
appIdSuffix=.tvhome
```

`auramusic/local.properties` (the set-up script adds the `sdk.dir` line):

```properties
release.storeFile=keystore/tvhome.keystore
release.storePassword=YOUR_KEY_PASSWORD
release.keyAlias=tvhome
release.keyPassword=YOUR_KEY_PASSWORD
debug.storeFile=keystore/tvhome.keystore
debug.storePassword=YOUR_KEY_PASSWORD
debug.keyAlias=tvhome
debug.keyPassword=YOUR_KEY_PASSWORD
custom.appIdSuffix=.tvhome
```

A debug key for the launcher / remap: `keytool -genkeypair -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`.

## 4. Build and install

| App | Mac | Windows | Time |
|---|---|---|---|
| Launcher | `bash tv-home/launcher/build-and-install-mac.sh` | `.\tv-home\launcher\build-and-install.ps1` | seconds |
| Remote Remap | `bash tv-home/remap/build-and-install.sh` | `.\tv-home\remap\build-and-install.ps1` | seconds |
| DodoTube | `bash smarttube/build-and-install-tv.sh` | `.\smarttube\build-and-install-tv.ps1` | ~3 min first time |
| DodoMusic | `bash auramusic/build-and-install-tv.sh` | `.\auramusic\build-and-install-tv.ps1` | ~20 min first time, then ~5 |
| Debloat | `bash tv-home/tools/debloat-mac.sh <list.txt> [--restore]` | `.\tv-home\tools\debloat.ps1 <list.txt> [-Restore]` | seconds |

- `--build` (Mac) / `-BuildOnly` (Windows) builds without installing; `--install` re-installs the last build.
- DodoTube and DodoMusic are only ever **updated in place** (`adb install -r`): sign-ins and settings
  survive. If the TV refuses an update because the key changed, restore the original key: don't uninstall.
- Run one Gradle build per app at a time: two builds in the same folder corrupt each other's output.
- The launcher script makes it the default home screen and disables the stock launcher;
  `tv-home/launcher/restore-stock-launcher.ps1` undoes that.

## 5. Debloat lists

`tv-home/examples/<device>/debloat.txt`: packages are **disabled for user 0, never uninstalled**, so
`--restore` brings everything back. Lists are included for the Xiaomi Mi Box S (`mi-box-s-mibox4`, plus
`extra.txt`) and the Xiaomi TV Box S 3rd gen. Back up your own package list first:

```bash
adb shell pm list packages -e > backup-enabled-before.txt
```
