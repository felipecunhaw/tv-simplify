#!/bin/sh
# Installs the home screen on your TV and makes it the default. Only needs adb.
#
#   sh install.sh <TV_IP>                          your TV apps, picked for you
#   sh install.sh <TV_IP> "com.netflix.ninja,com.google.android.youtube.tv"
#
# Needs out/tvhome.apk: build it first with  sh build.sh  (or build.ps1 on Windows).
set -e
cd "$(dirname "$0")"

TARGET="$1"; APPS="$2"
[ -n "$TARGET" ] || { echo "Usage: sh install.sh <TV_IP> [\"app1,app2,...\"]" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || {
  echo "adb is missing. Install it first:" >&2
  echo "  Mac:     brew install android-platform-tools" >&2
  echo "  Windows: https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1; }

# An IP gets :5555 added; an adb serial (like emulator-5554) is used as is.
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac

state=$(adb -s "$TV" get-state 2>&1 || true)
case "$state" in
  device) ;;
  *unauthorized*) echo "Your TV is asking \"Allow debugging?\". Press Allow (tick \"Always allow\"), then run this again." >&2; exit 1 ;;
  *) echo "Can't reach $TV. Check the TV is on, on the same Wi-Fi, and has USB/network debugging turned on." >&2; exit 1 ;;
esac

APK=out/tvhome.apk
[ -f "$APK" ] || { echo "out/tvhome.apk not found. Build it first: sh build.sh" >&2; exit 1; }

echo "Installing… If your TV shows a Google Play Protect message, follow it on the TV to allow the install."
if ! out=$(adb -s "$TV" install -r "$APK" 2>&1); then
  case "$out" in
    *UPDATE_INCOMPATIBLE*) echo "Replacing an older copy signed with a different key…"
                           adb -s "$TV" uninstall com.tvhome.example >/dev/null; adb -s "$TV" install "$APK" ;;
    *) echo "$out" >&2; exit 1 ;;
  esac
fi

if [ -n "$APPS" ]; then
  # The stamp makes the app apply this list once, even if Android later replays this start.
  adb -s "$TV" shell am start -n com.tvhome.example/.MainActivity --es apps "\"$APPS\"" --es stamp "$(date +%s)" >/dev/null
fi

ME=com.tvhome.example/.MainActivity
sh_tv() { adb -s "$TV" shell "$@" | tr -d '\r'; }
current_home() { sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }

# Make it the default home: the role (Android 10+) and the classic way.
if [ "$(sh_tv getprop ro.build.version.sdk)" -ge 29 ]; then
  sh_tv cmd role add-role-holder --user 0 android.app.role.HOME com.tvhome.example >/dev/null 2>&1 || true
fi
sh_tv cmd package set-home-activity "$ME" >/dev/null 2>&1 || true

# Most TVs' own launchers still win when you press Home. The fix is to turn them off
# (not uninstall). We save what we turned off on the TV, so undo.sh can turn it back on.
if [ "$(current_home)" != "$ME" ]; then
  others=$(sh_tv cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
    | grep / | sed 's|/.*||; s/^ *//' | grep -vx com.tvhome.example | grep -v '^com.android.tv.settings$' | sort -u)
  if [ -n "$others" ]; then
    echo "Your TV's own home screen keeps taking over:"
    echo "$others" | sed 's/^/  /'
    answer=${YES:+y}
    if [ -z "$answer" ]; then
      printf "Turn it off so Home opens your new home screen? You can undo this with undo.sh. [y/N] "
      read answer </dev/tty || answer=n
    fi
    case "$answer" in
      y|Y|yes)
        for p in $others; do
          sh_tv pm disable-user --user 0 "$p" >/dev/null && sh_tv "echo $p >> /data/local/tmp/tvhome-disabled.txt"
        done
        sleep 1
        sh_tv cmd package set-home-activity "$ME" >/dev/null 2>&1 || true ;;
      *) echo "Left it on. Your TV may keep opening its own home screen." ;;
    esac
  fi
fi

adb -s "$TV" shell input keyevent KEYCODE_HOME
if [ "$(current_home)" = "$ME" ]; then
  echo "Done. Press Home on your remote to see it."
else
  echo "Installed, but your TV still opens its own home screen. If it asks which home screen to use, pick \"Home\" and choose Always."
fi
