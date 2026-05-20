#!/usr/bin/env bash
# Import legacy sprites and animation frames into GalaxyMonkey/Assets.xcassets.
#
# Imageset mappings create <Name>.imageset/ with a single 1x PNG and a
# Contents.json. SpriteCatalog's enum rawValues must match <Name>.
#
# Atlas mappings create <Name>.spriteatlas/ with zero-padded frames.
# AnimationCatalog's AnimationSet rawValues must match <Name>.
#
# Source paths are resolved against $PIPELINE/normalized first, then
# $PIPELINE root (so `drawable/earth.png` and `animations/.../frame_01.png`
# both work without copying assets between dirs).
#
# Run from repo root:  ./sprite-pipeline/import-to-xcassets.sh

set -euo pipefail

PIPELINE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$PIPELINE/.." && pwd)"
ASSETS="$ROOT/GalaxyMonkey/Assets.xcassets"
SRC="$PIPELINE/normalized"

[[ -d "$SRC" ]] || { echo "Missing $SRC — run port-legacy first."; exit 1; }
[[ -d "$ASSETS" ]] || { echo "Missing $ASSETS"; exit 1; }

resolve_src() {
  # Resolves a mapping path against $SRC first then $PIPELINE.
  local rel="$1"
  if [[ -f "$SRC/$rel" ]]; then
    echo "$SRC/$rel"
  elif [[ -f "$PIPELINE/$rel" ]]; then
    echo "$PIPELINE/$rel"
  else
    echo ""
  fi
}

# -----------------------------------------------------------------------------
# Imagesets
# -----------------------------------------------------------------------------

mappings=(
  "Player|player/player.png"
  "Bullet|projectiles/bullet.png"
  "EnemyBullet|projectiles/enemybullet.png"
  "Bomb|projectiles/bomb.png"
  "PreppyLeft|enemies/preppy_left.png"
  "PreppyRight|enemies/preppy_right.png"
  "WhiteLeft|enemies/white_left.png"
  "WhiteRight|enemies/white_right.png"
  "ShadyLeft|enemies/shady_left.png"
  "ShadyRight|enemies/shady_right.png"
  "Gorilla|enemies/gorilla.png"
  # Planet backdrop set + glow halo (sprite-pipeline/drawable/).
  "Earth|drawable/earth.png"
  "Sun|drawable/sun.png"
  "Mercury|drawable/mercury.png"
  "Venus|drawable/venus.png"
  "Mars|drawable/mars.png"
  "Jupiter|drawable/jupiter.png"
  "Saturn|drawable/saturn.png"
  "Uranus|drawable/uranus.png"
  "Neptune|drawable/neptune.png"
  "Glow|drawable/glow.png"
)

for entry in "${mappings[@]}"; do
  name="${entry%%|*}"
  src_rel="${entry#*|}"
  src_path="$(resolve_src "$src_rel")"
  if [[ -z "$src_path" ]]; then
    echo "skip $name (missing $src_rel)"
    continue
  fi
  dest_dir="$ASSETS/${name}.imageset"

  # If a SpriteCook-canonical `*_sc.png` already exists in this imageset,
  # the legacy import is superseded. Don't copy the legacy file and don't
  # overwrite Contents.json — the *_sc.png is the chosen art (see
  # spritecook-assets.json).
  if compgen -G "$dest_dir/*_sc.png" >/dev/null; then
    echo "skip $name (sc canonical present in $dest_dir)"
    continue
  fi

  mkdir -p "$dest_dir"
  cp "$src_path" "$dest_dir/$(basename "$src_path")"
  cat > "$dest_dir/Contents.json" <<EOF
{
  "images" : [
    {
      "filename" : "$(basename "$src_path")",
      "idiom" : "universal",
      "scale" : "1x"
    },
    { "idiom" : "universal", "scale" : "2x" },
    { "idiom" : "universal", "scale" : "3x" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
EOF
  echo "imported imageset $name ← $src_rel"
done

# -----------------------------------------------------------------------------
# Spriteatlases (animation frame sequences)
# -----------------------------------------------------------------------------
#
# Each mapping declares the atlas name, the source directory (relative to
# $SRC or $PIPELINE), a glob, and a prefix for the renamed frames. Frames are
# zero-padded to 2 digits so AnimationCatalog's alpha-sort plays them in
# numeric order. Existing frame numbers in the source are read from a regex
# so e1.png..e25.png and frame_01.png..frame_12.png both work.
#
# Format: NAME|SRC_DIR|GLOB|PREFIX
atlases=(
  "BombExplosionAnim|drawable|e[0-9]*.png|bomb"
)

for entry in "${atlases[@]}"; do
  IFS='|' read -r name src_dir glob prefix <<< "$entry"
  resolved_dir=""
  if [[ -d "$SRC/$src_dir" ]]; then
    resolved_dir="$SRC/$src_dir"
  elif [[ -d "$PIPELINE/$src_dir" ]]; then
    resolved_dir="$PIPELINE/$src_dir"
  else
    echo "skip atlas $name (missing dir $src_dir)"
    continue
  fi

  dest_dir="$ASSETS/${name}.spriteatlas"
  rm -rf "$dest_dir"
  mkdir -p "$dest_dir"

  # Collect matching files into an array, sorted by the numeric suffix in
  # their basename. `sort -V` handles "e1, e2, ..., e10, e25" correctly.
  mapfile -t files < <(cd "$resolved_dir" && ls $glob 2>/dev/null | sort -V)
  if [[ ${#files[@]} -eq 0 ]]; then
    echo "skip atlas $name (no files matched $glob in $src_dir)"
    rmdir "$dest_dir" 2>/dev/null || true
    continue
  fi

  idx=1
  for f in "${files[@]}"; do
    padded=$(printf "%02d" "$idx")
    cp "$resolved_dir/$f" "$dest_dir/${prefix}_${padded}.png"
    idx=$((idx + 1))
  done

  cat > "$dest_dir/Contents.json" <<EOF
{
  "info" : { "author" : "xcode", "version" : 1 },
  "properties" : { "provides-namespace" : true }
}
EOF
  echo "imported atlas $name (${#files[@]} frames) ← $src_dir"
done

echo
echo "Done. Rebuild via ./run-sim.sh"
