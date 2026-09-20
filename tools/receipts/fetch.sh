#!/usr/bin/env bash
# Fetches the receipt fixtures the restaurant reader is calibrated against.
#
# They are not in this repository. They are other people's photographs under their own
# licences, and a project has no business redistributing someone's holiday snap of a
# Kathmandu dinner bill because it happened to be useful for testing. This script gets
# them again, into a directory git is told to ignore.
#
# Two kinds, because they fail differently:
#
#   SROIE      scanned restaurant receipts from the ICDAR 2019 benchmark. Flat, square and
#              high contrast, so they test the vocabulary and the column calibration with
#              the tilt taken out of the question.
#   Commons    photographs of real bills, freely licensed. Tilted, shadowed and curled,
#              which is what a phone actually produces and what Deskew exists for.
set -euo pipefail

DEST="$(cd "$(dirname "$0")/../.." && pwd)/app/src/androidTest/assets"
mkdir -p "$DEST"

SROIE="https://raw.githubusercontent.com/zzzDavid/ICDAR-2019-SROIE/master/data/img"
for id in 000 001 002 003 004 005 006 007; do
  curl -fsSL "$SROIE/${id}.jpg" -o "$DEST/bill-sroie-${id}.jpg" 2>/dev/null || true
done

commons() {
  curl -fsSL -A "household-order-splitter/dev" \
    "https://commons.wikimedia.org/wiki/Special:FilePath/$1" -o "$DEST/$2" || true
}
commons "Restaurant%20Bill%201%202013-07-08.jpg"  "bill-photo-1.jpg"
commons "Croatia%20pizza%20receipt.jpg"           "bill-photo-2.jpg"

ls -la "$DEST" | grep bill- || echo "nothing fetched"
