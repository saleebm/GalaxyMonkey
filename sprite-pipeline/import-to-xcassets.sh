#!/usr/bin/env bash
# Import normalized legacy sprites into GalaxyMonkey/Assets.xcassets.
#
# Each mapping creates an imageset with a Contents.json and a single 1x PNG
# under GalaxyMonkey/Assets.xcassets/<Name>.imageset/. SpriteCatalog's enum
# rawValues match the <Name> here.
#
# Run from repo root:  ./sprite-pipeline/import-to-xcassets.sh

set -euo pipefail

PIPELINE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$PIPELINE/.." && pwd)"
ASSETS="$ROOT/GalaxyMonkey/Assets.xcassets"
SRC="$PIPELINE/normalized"

[[ -d "$SRC" ]] || { echo "Missing $SRC — run port-legacy first."; exit 1; }
[[ -d "$ASSETS" ]] || { echo "Missing $ASSETS"; exit 1; }

# Map: imageset name → source file (relative to $SRC).
mappings=(
  "Player|player/player.png"
  "Bullet|projectiles/bullet.png"
  "EnemyBullet|projectiles/enemybullet.png"
  "Bomb|projectiles/bomb.png"
  "Banana|collectibles/banana.png"
  "RottenBanana|collectibles/rb.png"
  "PreppyLeft|enemies/preppy_left.png"
  "PreppyRight|enemies/preppy_right.png"
  "WhiteLeft|enemies/white_left.png"
  "WhiteRight|enemies/white_right.png"
  "ShadyLeft|enemies/shady_left.png"
  "ShadyRight|enemies/shady_right.png"
  "Gorilla|enemies/gorilla.png"
)

for entry in "${mappings[@]}"; do
  name="${entry%%|*}"
  src_rel="${entry#*|}"
  src_path="$SRC/$src_rel"
  [[ -f "$src_path" ]] || { echo "skip $name (missing $src_rel)"; continue; }
  dest_dir="$ASSETS/${name}.imageset"
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
  echo "imported $name ← $src_rel"
done

echo
echo "Done. Rebuild via ./run-sim.sh"
