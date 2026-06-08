#!/usr/bin/env bash
# Slices a 5120x640 SpriteCook walk spritesheet into a .spriteatlas folder
# with nested imagesets. Drops frame 1 (always glitched, per spritecook_3d_rotation_limit).
# Result: 7 frames numbered _01.._07 inside <Atlas>.spriteatlas/<base>_NN.imageset/.
set -euo pipefail

SHEET="$1"          # e.g. sprite-pipeline/walk-sheets/preppy_walk_sheet.png
ATLAS_DIR="$2"      # e.g. GalaxyMonkey/Assets.xcassets/PreppyLeftWalkAnim.spriteatlas
FRAME_BASE="$3"     # e.g. preppy_left_walk

FW=640
FH=640
mkdir -p "$ATLAS_DIR"

cat > "$ATLAS_DIR/Contents.json" <<EOF
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

# Sheet frames 2..8 → atlas frames 01..07. Frame 1 dropped.
for i in 1 2 3 4 5 6 7; do
  SRC_IDX=$((i + 1))             # 2..8
  XOFF=$(((SRC_IDX - 1) * FW))   # 640, 1280, ..., 4480
  NN=$(printf "%02d" "$i")
  IMG_DIR="$ATLAS_DIR/${FRAME_BASE}_${NN}.imageset"
  mkdir -p "$IMG_DIR"
  sips --cropOffset 0 "$XOFF" --cropToHeightWidth "$FH" "$FW" "$SHEET" \
       --out "$IMG_DIR/${FRAME_BASE}_${NN}.png" >/dev/null
  cat > "$IMG_DIR/Contents.json" <<EOF
{
  "images" : [
    {
      "filename" : "${FRAME_BASE}_${NN}.png",
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

echo "Sliced $SHEET → $ATLAS_DIR (7 frames)"
