#!/usr/bin/env bash
# Builds the opposite-direction walk atlas by sips-horizontal-flipping every
# frame of the source atlas. Per the spritecook_mirror_lesson, this is the
# canonical L↔R pair strategy for GalaxyMonkey enemies.
set -euo pipefail

SRC_ATLAS="$1"      # GalaxyMonkey/Assets.xcassets/PreppyLeftWalkAnim.spriteatlas
DST_ATLAS="$2"      # GalaxyMonkey/Assets.xcassets/PreppyRightWalkAnim.spriteatlas
SRC_BASE="$3"       # preppy_left_walk
DST_BASE="$4"       # preppy_right_walk

mkdir -p "$DST_ATLAS"
cat > "$DST_ATLAS/Contents.json" <<EOF
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "provides-namespace" : true
  }
}
EOF

for i in 01 02 03 04 05 06 07; do
  SRC_PNG="$SRC_ATLAS/${SRC_BASE}_${i}.imageset/${SRC_BASE}_${i}.png"
  DST_DIR="$DST_ATLAS/${DST_BASE}_${i}.imageset"
  mkdir -p "$DST_DIR"
  sips -f horizontal "$SRC_PNG" --out "$DST_DIR/${DST_BASE}_${i}.png" >/dev/null
  cat > "$DST_DIR/Contents.json" <<EOF
{
  "images" : [
    {
      "filename" : "${DST_BASE}_${i}.png",
      "idiom" : "universal",
      "scale" : "1x"
    },
    {
      "idiom" : "universal",
      "scale" : "2x"
    },
    {
      "idiom" : "universal",
      "scale" : "3x"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF
done

echo "Flipped $SRC_ATLAS → $DST_ATLAS (7 frames)"
