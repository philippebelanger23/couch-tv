#!/bin/sh
# Download the TV APKs into dist/ (gitignores them).
# Default: this copy's latest GitHub release. Override:
#   RELEASE_BASE=https://github.com/Seagey/couch-tv/releases/latest/download
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE=${RELEASE_BASE:-https://github.com/philippebelanger23/couch-tv/releases/latest/download}
mkdir -p "$ROOT/dist"
echo "Fetching APKs from $BASE"
curl -fL -o "$ROOT/dist/couch-tv-home.apk" "$BASE/couch-tv-home.apk"
curl -fL -o "$ROOT/dist/couch-tv-ontv.apk" "$BASE/couch-tv-ontv.apk"
ls -lh "$ROOT/dist"/couch-tv-*.apk
