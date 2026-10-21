#!/usr/bin/env bash
# Undo --set-home: re-enable the stock launcher and clear Wii TV as the default.
#   scripts/restore-home.sh 192.168.1.50
set -euo pipefail
TV="${1:-${TV_HOST:-}}"
[ -n "$TV" ] || { echo "usage: $0 <TV_IP>" >&2; exit 1; }
[[ "$TV" == *:* ]] || TV="$TV:5555"
adb connect "$TV" >/dev/null
adb -s "$TV" shell pm clear-package-preferred-activities com.example.wiitv 2>/dev/null || true
# Re-enable launchers that may have been disabled by hand.
for pkg in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.amazon.tv.launcher; do
  adb -s "$TV" shell pm enable "$pkg" >/dev/null 2>&1 && echo "enabled $pkg" || true
done
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Stock home restored (choose it if the TV asks)."
