#!/usr/bin/env bash
# macOS: build the redesigned AuraMusic ("DodoMusic") and update it on the TV box.
#   bash auramusic/build-and-install-tv.sh             build + update (first build ~20 min)
#   bash auramusic/build-and-install-tv.sh --build     build only
#   bash auramusic/build-and-install-tv.sh --install   update with the last build
# Needs JDK 21, and local.properties with sdk.dir, the tvhome keystore and custom.appIdSuffix=.tvhome.
set -euo pipefail
cd "$(dirname "$0")"
source ../tools/mac-env.sh

[ -f local.properties ] || { echo "local.properties is missing - run: bash tools/setup-new-mac.sh" >&2; exit 1; }
[ -f app/keystore/tvhome.keystore ] || { echo "app/keystore/tvhome.keystore is missing - restore it; a new key can't update the installed app" >&2; exit 1; }

apk=app/build/outputs/apk/fossTvArmeabi/release/app-foss-tv-armeabi-release.apk
if [ "${1:-}" != "--install" ]; then
  use_jdk 21
  chmod +x gradlew
  start=$(date +%s)
  ./gradlew assembleFossTvArmeabiRelease --no-configuration-cache --console=plain
  echo "Built in $(( ($(date +%s) - start) / 60 )) min: $apk"
fi
[ "${1:-}" = "--build" ] && exit 0

tv_update "$apk" com.auramusic.app.tvhome DodoMusic
