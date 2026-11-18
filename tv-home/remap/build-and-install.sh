#!/usr/bin/env bash
# macOS: build the Remote Remap app (accessibility service that remaps remote buttons), install and enable it.
# Same steps as build-and-install.ps1 (Windows).
#   bash tv-home/remap/build-and-install.sh             build + install + enable
#   bash tv-home/remap/build-and-install.sh --build     build only
#   bash tv-home/remap/build-and-install.sh --disable   turn the service off
set -euo pipefail
cd "$(dirname "$0")"
source ../../tools/mac-env.sh
SVC="com.mihir.remap/com.mihir.remap.RemapService"
tvsh() { "$ADB" -s "$DEVICE" shell "$1" | tr -d '\r'; }

if [ "${1:-}" = "--disable" ]; then
  tv_connect
  cur=$(tvsh "settings get secure enabled_accessibility_services" | tr ':' '\n' | grep -v -e '^null$' -e "^$SVC$" -e '^$' | paste -sd: - || true)
  if [ -n "$cur" ]; then tvsh "settings put secure enabled_accessibility_services $cur"; else tvsh "settings delete secure enabled_accessibility_services"; fi
  echo "Remote Remap disabled."; exit 0
fi

use_jdk 17  # min SDK 28 = Android 9 (Mi Box S MIBOX4); was 30
BT="$ANDROID_HOME/build-tools/34.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
B=build
rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  --min-sdk-version 28 --target-sdk-version 30 "$B/res.zip"
javac -encoding UTF-8 -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')
"$BT/d8" --min-api 28 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f debug.keystore ] || { echo "debug.keystore is missing - restore it; a new key can't update the installed app" >&2; exit 1; }
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --out "$B/remap.apk" "$B/aligned.apk"
cp "$B/remap.apk" RemoteRemap.apk   # keep a copy you can reinstall from
echo "built RemoteRemap.apk"
[ "${1:-}" = "--build" ] && exit 0

tv_connect
"$ADB" -s "$DEVICE" install -r "$B/remap.apk"
# Enable the accessibility service, keeping any others already enabled.
cur=$(tvsh "settings get secure enabled_accessibility_services" | tr ':' '\n' | grep -v -e '^null$' -e "^$SVC$" -e '^$' || true)
all=$(printf '%s\n%s\n' "$cur" "$SVC" | grep -v '^$' | paste -sd: -)
tvsh "settings put secure enabled_accessibility_services $all"
tvsh "settings put secure accessibility_enabled 1"
echo "enabled: $(tvsh 'settings get secure enabled_accessibility_services')"
