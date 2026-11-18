#!/usr/bin/env bash
# macOS version of debloat.ps1, using adb: disable (or re-enable) every package in a list.
#   bash tv-home/tools/debloat-mac.sh tv-home/examples/xiaomi-tv-box-s-3rd-gen/debloat.txt            disable
#   bash tv-home/tools/debloat-mac.sh tv-home/examples/xiaomi-tv-box-s-3rd-gen/debloat.txt --restore  re-enable
set -euo pipefail
source "$(dirname "$0")/../../tools/mac-env.sh"
LIST="${1:?usage: debloat-mac.sh <list.txt> [--restore]}"
tv_connect
# One command per shell call: chained pm commands can silently stop after the first.
grep -vE '^\s*(#|$)' "$LIST" | sed 's/#.*$//' | while read -r p; do
  [ -z "$p" ] && continue
  if [ "${2:-}" = "--restore" ]; then cmd="pm enable $p"; else cmd="pm disable-user --user 0 $p"; fi
  printf '%-46s %s\n' "$p" "$("$ADB" -s "$DEVICE" shell "$cmd" </dev/null 2>&1 | tr -d '\r')"
done
