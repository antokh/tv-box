#!/usr/bin/env bash
# macOS: build the launcher with bare SDK tools (no Gradle) and install it on the TV as the home screen.
# Same steps as build-and-install.ps1 (Windows), but using adb from Homebrew.
# (build-and-install.sh is the upstream script; it installs through tools/tv.py instead of adb.)
#   bash tv-home/launcher/build-and-install-mac.sh           build + install
#   bash tv-home/launcher/build-and-install-mac.sh --build   build only
set -euo pipefail
cd "$(dirname "$0")"
source ../../tools/mac-env.sh
use_jdk 17
BT="$ANDROID_HOME/build-tools/34.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
# Stock launcher to disable: launcherx = Google TV (old box), tvlauncher = Android TV 9 (Mi Box S MIBOX4).
STOCK_LAUNCHERS="${STOCK_LAUNCHER:-com.google.android.apps.tv.launcherx com.google.android.tvlauncher}"
MIN_SDK=28  # Android 9 (Mi Box S MIBOX4); was 30 when only the Google TV box existed
B=build

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  -A assets --min-sdk-version "$MIN_SDK" --target-sdk-version 30 "$B/res.zip"
javac -encoding UTF-8 -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')
"$BT/d8" --min-api "$MIN_SDK" --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f debug.keystore ] || { echo "debug.keystore is missing - restore it; a new key can't update the installed launcher" >&2; exit 1; }
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --out "$B/tvhome.apk" "$B/aligned.apk"
echo "built $B/tvhome.apk"
[ "${1:-}" = "--build" ] && exit 0

tv_connect
tvsh() { "$ADB" -s "$DEVICE" shell "$1"; }
"$ADB" -s "$DEVICE" install -r "$B/tvhome.apk"
tvsh "pm grant com.mihir.tvhome android.permission.ACCESS_FINE_LOCATION" || true
tvsh "cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity"
for sl in $STOCK_LAUNCHERS; do
  tvsh "pm path $sl" </dev/null | grep -q package: && tvsh "pm disable-user --user 0 $sl" </dev/null || true
done
tvsh "pm disable-user --user 0 com.google.android.tungsten.setupwraith" || true
tvsh "cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity"
tvsh "am force-stop com.android.tv.settings"
tvsh "input keyevent KEYCODE_HOME"
echo "Installed. Press Home on the remote."
