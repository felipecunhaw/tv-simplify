#!/bin/sh
# Removes the home screen and turns your TV's own home screen back on.
# Usage: sh undo.sh <TV_IP>
set -e
[ -n "$1" ] || { echo "Usage: sh undo.sh <TV_IP>" >&2; exit 1; }
case "$1" in
  *.*.*.*:*) TV=$1 ;;
  *.*.*.*)   TV=$1:5555 ;;
  *)         TV=$1 ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac

# Turn back on whatever install.sh turned off, before removing this home screen.
for p in $(adb -s "$TV" shell cat /data/local/tmp/tvhome-disabled.txt 2>/dev/null | tr -d '\r' | sort -u); do
  adb -s "$TV" shell pm enable "$p" >/dev/null && echo "Turned $p back on"
done
adb -s "$TV" shell rm -f /data/local/tmp/tvhome-disabled.txt
adb -s "$TV" uninstall com.tvhome.example >/dev/null 2>&1 || true
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Removed. Your TV is back to its own home screen."
