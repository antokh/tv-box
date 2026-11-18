# Shared settings for the macOS scripts. Sourced, not run:  source "$(dirname "$0")/../tools/mac-env.sh"
# Override any of these before running a script, e.g.:  DEVICE=192.168.1.50:5555 bash smarttube/build-and-install-tv.sh

# The TV box (Wi-Fi ADB). Change here if the box gets a new address.
DEVICE="${DEVICE:-192.168.1.100:5555}"  # your TV box: IP:port shown in its Developer options

# The whole toolchain (JDK 17 + 21, Android SDK incl. adb/NDK/CMake, Gradle cache) lives NEXT TO the
# project folder, never on the Mac's small internal disk and never via Homebrew:
#   <folder containing "TV Box">/tv-box-toolchain/{jdk/17,jdk/21,android-sdk,gradle}
# tools/setup-new-mac.sh downloads it there.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"  # $0 = this file when sourced from zsh
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$(dirname "$PROJECT_ROOT")/tv-box-toolchain}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLCHAIN_DIR/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLCHAIN_DIR/gradle}"
mkdir -p "$ANDROID_HOME" "$GRADLE_USER_HOME"
JDK17="${JDK17:-$TOOLCHAIN_DIR/jdk/17/Contents/Home}"
JDK21="${JDK21:-$TOOLCHAIN_DIR/jdk/21/Contents/Home}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

use_jdk() {  # use_jdk 17 | 21
  local home; [ "$1" = 21 ] && home="$JDK21" || home="$JDK17"
  [ -d "$home" ] || { echo "JDK $1 not found at $home - run: bash tools/setup-new-mac.sh" >&2; exit 1; }
  export JAVA_HOME="$home"; export PATH="$JAVA_HOME/bin:$PATH"
}

tv_connect() { "$ADB" connect "$DEVICE" >/dev/null 2>&1 || true; }

# In-place update only: keeps the app's data, so you stay signed in.
# NEVER uninstall or "pm clear" to get past an error - that signs her out and wipes settings.
tv_update() {  # tv_update <apk> <package> <name>
  tv_connect
  # Time limit: a dropped Wi-Fi link once left "adb install" hanging for 10+ minutes with no message.
  local out rc; out="$(perl -e 'alarm shift; exec @ARGV' "${INSTALL_TIMEOUT_S:-300}" "$ADB" -s "$DEVICE" install -r "$1" 2>&1)"; rc=$?; echo "$out"
  if [ $rc -eq 142 ]; then
    echo "Install timed out after ${INSTALL_TIMEOUT_S:-300}s: the TV is asleep or its Wi-Fi dropped. Wake it and rerun with --install (nothing was changed on the TV)." >&2
    exit 1
  fi
  if echo "$out" | grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match"; then
    echo "The TV refused the update: this APK is signed with a different key than the installed $3." >&2
    echo "Restore the original keystore (see docs/BUILDING.md). Do NOT uninstall the app - that would sign you out." >&2
    exit 1
  fi
  echo "$out" | grep -q Success || { echo "adb install failed" >&2; exit 1; }
  echo "Updated $2 on $DEVICE (data and sign-in kept)"
}
