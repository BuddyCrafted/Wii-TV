#!/usr/bin/env bash
# Build the Wii TV launcher and install it on a TV over wireless ADB.
#
#   scripts/deploy.sh 192.168.1.50            # build, install, launch
#   scripts/deploy.sh 192.168.1.50 --set-home # ...and make it the home screen
#   scripts/deploy.sh 192.168.1.50 --no-build # reinstall the last build
set -euo pipefail

usage() { sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

TV="${1:-${TV_HOST:-}}"
[ -n "$TV" ] || usage
shift || true
[[ "$TV" == *:* ]] || TV="$TV:5555"

SET_HOME=0
BUILD=1
for arg in "$@"; do
  case "$arg" in
    --set-home) SET_HOME=1 ;;
    --no-build) BUILD=0 ;;
    *) usage ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.example.wiitv"
ACTIVITY="$PKG/.MainActivity"

command -v adb >/dev/null || { echo "adb not found: install Android platform-tools" >&2; exit 1; }

if [ "$BUILD" = 1 ]; then
  echo "==> Building APK"
  (cd "$ROOT/android" && ./gradlew --quiet assembleDebug)
fi
[ -f "$APK" ] || { echo "APK not found at $APK" >&2; exit 1; }

echo "==> Connecting to $TV (accept the prompt on the TV the first time)"
adb connect "$TV"
adb -s "$TV" wait-for-device

echo "==> Installing"
adb -s "$TV" install -r "$APK"

if [ "$SET_HOME" = 1 ]; then
  echo "==> Setting Wii TV as the home screen"
  if adb -s "$TV" shell cmd package set-home-activity "$ACTIVITY"; then
    echo "    Done. Press HOME on the remote to check."
  else
    echo "    This device refused set-home-activity (common on Fire OS / some Google TV builds)." >&2
    echo "    See README.md > 'Making it the home screen' for alternatives." >&2
  fi
fi

echo "==> Launching"
adb -s "$TV" shell am start -n "$ACTIVITY"
