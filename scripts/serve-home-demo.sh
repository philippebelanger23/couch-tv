#!/bin/sh
# Serve the TV home in a browser (no TV). From the repo root so app logos resolve.
# Open /tv-home/web/index.html?mock=1
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PORT=${PORT:-8080}
cd "$ROOT"
echo "TV home demo: http://127.0.0.1:$PORT/tv-home/web/index.html?mock=1"
echo "Repo root must be the document root (app logos live in remote/src/app-logos/)."
exec python3 -m http.server "$PORT" --bind 127.0.0.1
