#!/bin/sh
# Download the TV APKs into dist/ (gitignores them). Default: latest Seagey release.
# Override with RELEASE_BASE=https://github.com/<owner>/<repo>/releases/latest/download
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE=${RELEASE_BASE:-https://github.com/Seagey/couch-tv/releases/latest/download}
mkdir -p "$ROOT/dist"
echo "Fetching APKs from $BASE"
curl -fL -o "$ROOT/dist/couch-tv-home.apk" "$BASE/couch-tv-home.apk"
curl -fL -o "$ROOT/dist/couch-tv-ontv.apk" "$BASE/couch-tv-ontv.apk"
ls -lh "$ROOT/dist"/couch-tv-*.apk
