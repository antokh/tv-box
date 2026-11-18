#!/usr/bin/env bash
# macOS: build the redesigned SmartTube ("DodoTube") and update it on the TV box.
#   bash smarttube/build-and-install-tv.sh             build + update
#   bash smarttube/build-and-install-tv.sh --build     build only
#   bash smarttube/build-and-install-tv.sh --install   update with the last build
# Needs JDK 17, local.properties (sdk.dir) and keystore.properties (tvhome key + appIdSuffix=.tvhome).
set -euo pipefail
cd "$(dirname "$0")"
source ../tools/mac-env.sh

[ -f keystore.properties ] || { echo "keystore.properties is missing - see docs/BUILDING.md" >&2; exit 1; }
[ -f tvhome.keystore ] || { echo "tvhome.keystore is missing - restore it; a new key can't update the installed app" >&2; exit 1; }

if [ "${1:-}" != "--install" ]; then
  use_jdk 17
  chmod +x gradlew
  start=$(date +%s)
  ./gradlew assembleStstableRelease -x lintVitalStstableRelease --console=plain
  echo "Built in $(( ($(date +%s) - start) / 60 )) min"
fi
[ "${1:-}" = "--build" ] && exit 0

apk=$(ls -t smarttubetv/build/outputs/apk/ststable/release/SmartTube_stable_*_armeabi-v7a.apk | head -1)
tv_update "$apk" org.smarttube.stable.tvhome DodoTube
