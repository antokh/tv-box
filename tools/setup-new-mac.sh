#!/usr/bin/env bash
# Install everything needed to build and install the TV box apps on a Mac (Apple Silicon or Intel).
# Everything goes into <folder containing "TV Box">/tv-box-toolchain (see tools/mac-env.sh), so the
# Mac's internal disk stays clean. No Homebrew, no sudo. Safe to re-run. See docs/BUILDING.md.
#   bash tools/setup-new-mac.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/tools/mac-env.sh"

case "$(uname -m)" in arm64) ARCH=aarch64 ;; *) ARCH=x64 ;; esac
DL="$TOOLCHAIN_DIR/downloads"; mkdir -p "$DL"
command -v git >/dev/null || echo "Note: git not found (only needed for source history): xcode-select --install"

echo "== JDK 17 + 21 (Eclipse Temurin) -> $TOOLCHAIN_DIR/jdk"
for v in 17 21; do
  if [ ! -x "$TOOLCHAIN_DIR/jdk/$v/Contents/Home/bin/java" ]; then
    curl -fL -o "$DL/jdk$v.tar.gz" "https://api.adoptium.net/v3/binary/latest/$v/ga/mac/$ARCH/jdk/hotspot/normal/eclipse"
    rm -rf "$TOOLCHAIN_DIR/jdk/$v"; mkdir -p "$TOOLCHAIN_DIR/jdk/$v"
    tar -xzf "$DL/jdk$v.tar.gz" -C "$TOOLCHAIN_DIR/jdk/$v" --strip-components 1
  fi
  "$TOOLCHAIN_DIR/jdk/$v/Contents/Home/bin/java" -version 2>&1 | head -1
done

echo "== Android command-line tools -> $ANDROID_HOME"
if [ ! -x "$SDKMANAGER" ]; then
  clt=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
        | grep -oE 'commandlinetools-mac-[0-9]+_latest\.zip' | sort -t- -k3 -n | tail -1)
  curl -fL -o "$DL/clt.zip" "https://dl.google.com/android/repository/$clt"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" "$ANDROID_HOME/cmdline-tools/cmdline-tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$DL/clt.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

use_jdk 17
echo "== Android SDK licences + packages (adb, platforms, build-tools, NDK, CMake)"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;30.0.3" "build-tools;34.0.0" \
  "platforms;android-36" "build-tools;36.0.0" "ndk;27.0.12077973" "cmake;3.22.1"

echo "== Point the Gradle projects at this SDK"
for p in "$ROOT/smarttube/local.properties" "$ROOT/auramusic/local.properties"; do
  if [ -f "$p" ]; then
    # Replace the old sdk.dir, keep everything else (signing settings, appIdSuffix).
    grep -v '^sdk.dir=' "$p" > "$p.tmp" || true
    { echo "sdk.dir=$ANDROID_HOME"; cat "$p.tmp"; } > "$p"; rm -f "$p.tmp"
  else
    echo "sdk.dir=$ANDROID_HOME" > "$p"
  fi
done
# auramusic/local.properties also needs the signing + suffix lines; recreate them if they're missing.
AP="$ROOT/auramusic/local.properties"
grep -q '^custom.appIdSuffix=' "$AP" || cat >> "$AP" <<'EOF'
release.storeFile=keystore/tvhome.keystore
release.storePassword=YOUR_KEY_PASSWORD
release.keyAlias=tvhome
release.keyPassword=YOUR_KEY_PASSWORD
debug.storeFile=keystore/tvhome.keystore
debug.storePassword=YOUR_KEY_PASSWORD
debug.keyAlias=tvhome
debug.keyPassword=YOUR_KEY_PASSWORD
# Installs next to the upstream app instead of replacing it.
custom.appIdSuffix=.tvhome
EOF

echo "== Unix line endings for gradlew / *.sh (Windows git checks them out as CRLF -> 'bash\\r: No such file')"
find "$ROOT" \( -name gradlew -o -name '*.sh' \) -not -path '*/build/*' -not -path '*/.git/*' -type f \
  -exec perl -pi -e 's/\r$//' {} +

echo "== Make scripts executable (zip files from Windows drop this)"
chmod +x "$ROOT"/smarttube/gradlew "$ROOT"/auramusic/gradlew "$ROOT"/smarttube/SharedModules/gradlew 2>/dev/null || true
find "$ROOT" -name '*.sh' -not -path '*/build/*' -exec chmod +x {} + 2>/dev/null || true

echo "== Check the signing keys are here"
for k in smarttube/tvhome.keystore auramusic/app/keystore/tvhome.keystore tv-home/launcher/debug.keystore tv-home/remap/debug.keystore; do
  [ -f "$ROOT/$k" ] && echo "  ok  $k" || echo "  MISSING  $k  <- restore it before building (see docs/BUILDING.md)"
done

rm -rf "$DL"
echo "== Done. Toolchain: $TOOLCHAIN_DIR ($(du -sh "$TOOLCHAIN_DIR" | cut -f1))"
echo "   Connect to the TV with:  \"$ADB\" connect $DEVICE  (accept the prompt on the TV)"
