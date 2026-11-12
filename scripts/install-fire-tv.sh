#!/bin/sh
# Unix wrapper. On Windows use scripts/install-fire-tv.ps1
#   TV_IP=192.168.1.110 sh scripts/install-fire-tv.sh
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
if [ -x "$ROOT/.venv/bin/python" ]; then
  PY="$ROOT/.venv/bin/python"
else
  python3 -m venv .venv
  .venv/bin/pip install -r bridge/requirements.txt
  PY="$ROOT/.venv/bin/python"
fi
if [ -n "$TV_IP" ]; then
  exec "$PY" "$ROOT/scripts/install-fire-tv.py" "$TV_IP" "$@"
fi
exec "$PY" "$ROOT/scripts/install-fire-tv.py" "$@"
