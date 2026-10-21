#!/usr/bin/env bash
# Save how the TV is set up before changing anything: the app lists (enabled, disabled, system,
# third-party), who owns Home, the settings tables, the build properties and a screenshot.
# Everything goes to backups/<model>/<date>/, which git ignores: these files carry the TV's serial
# number, android_id and network names, so keep them on your computer.
#
# Usage: TV_HOST=192.168.1.50 tools/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
export MSYS_NO_PATHCONV=1
tv() { "$PY" -I tools/tv.py "$@"; }

MODEL=$(tv 'getprop ro.product.model' | tr -d '\r\n' | tr -c 'A-Za-z0-9._-' '-')
DIR="backups/${MODEL:-tv}/$(date +%Y-%m-%d_%H%M%S)"
mkdir -p "$DIR"
echo "Saving to $DIR"

save() {
  echo "  $1"
  tv "$2" > "$DIR/$1" || echo "    (failed: $2)" >&2
}
save getprop.txt 'getprop'
save packages-all-paths.txt 'pm list packages -f'
save packages-enabled.txt 'pm list packages -e'
save packages-disabled.txt 'pm list packages -d'
save packages-system.txt 'pm list packages -s'
save packages-thirdparty.txt 'pm list packages -3'
save packages-incl-uninstalled.txt 'pm list packages -u'
save home-current.txt 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
save home-candidates.txt 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
save leanback-apps.txt 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER'
save settings-global.txt 'settings list global'
save settings-secure.txt 'settings list secure'
save settings-system.txt 'settings list system'
save meminfo.txt 'head -n 5 /proc/meminfo'
save storage.txt 'df -h /data'

SHOT=/sdcard/wii-tv-backup.png
if tv "screencap -p $SHOT" >/dev/null && tv --pull "$SHOT" "$DIR/screenshot.png" >/dev/null; then
  echo "  screenshot.png"
else
  echo "  (no screenshot)" >&2
fi
tv "rm -f $SHOT" >/dev/null || true
echo "Done. Keep $DIR private: it identifies your TV."
