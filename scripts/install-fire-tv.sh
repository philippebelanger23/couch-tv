#!/bin/sh
# Sideload couch-tv's home onto a Fire TV (Option A). Computer needed once, same Wi-Fi.
# Usage, from the repo root:
#   TV_IP=192.168.x.x sh scripts/install-fire-tv.sh
#
# Does not print or store the TV serial. Leaves Amazon's launcher installed.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
: "${TV_IP:?set TV_IP to the TV's IPv4 address (Settings, My Fire TV, About, Network)}"
PY="${PY:-$ROOT/.venv/bin/python}"
T="$ROOT/tv-home/tools/tvadb.py"
APK=${APK:-$ROOT/dist/couch-tv-home.apk}

if [ ! -x "$PY" ]; then
  echo "Creating .venv"
  python3 -m venv .venv
  .venv/bin/pip install -r bridge/requirements.txt
  PY=$ROOT/.venv/bin/python
fi

if [ ! -f "$APK" ]; then
  echo "No $APK — downloading"
  sh "$ROOT/scripts/fetch-apks.sh"
fi

if [ ! -f "$ROOT/config.json" ]; then
  cp "$ROOT/config.fire-tv.example.json" "$ROOT/config.json"
  echo "Wrote config.json from config.fire-tv.example.json. Put $TV_IP in tvs[0].ip and run again if you want that address saved."
fi

# Point the living-room entry at this TV without committing it (config.json is gitignored).
"$PY" - "$TV_IP" <<'PY'
import json, os, sys
path = "config.json"
ip = sys.argv[1]
with open(path) as f:
    cfg = json.load(f)
for tv in cfg.get("tvs") or []:
    if tv.get("id") == "living-room" or tv.get("ip") in ("SET_YOUR_TV_IP", "", None):
        tv["ip"] = ip
        break
else:
    sys.exit("config.json has no living-room / placeholder TV")
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
print("config.json tvs ip ->", ip)
PY

echo "Checking ADB on $TV_IP:5555"
"$PY" "$T" ping >/dev/null

echo "Read-only survey (model / homes / apps). Serial is not requested."
TV_IP="$TV_IP" "$PY" "$ROOT/tv-home/tools/survey-tv.py"

echo "Installing home APK and Home button watch (Amazon launcher stays on)"
TV_IP="$TV_IP" TV_NAME=living-room APK="$APK" sh "$ROOT/tv-home/tools/apply.sh"

# Extra grants used on Fire OS / TCL. Harmless if the op does not exist.
"$PY" "$T" shell "appops set app.couchtv.launcher GET_USAGE_STATS allow" >/dev/null 2>&1 || true
"$PY" "$T" shell "appops set app.couchtv.launcher SYSTEM_ALERT_WINDOW allow" >/dev/null 2>&1 || true
"$PY" "$T" shell "appops set app.couchtv.launcher AUTO_START allow" >/dev/null 2>&1 || true

echo
echo "Done. Press Home on the TV remote."
echo "Amazon's home may flash, then couch-tv. Undo: TV_IP=$TV_IP sh tv-home/tools/undo.sh"
