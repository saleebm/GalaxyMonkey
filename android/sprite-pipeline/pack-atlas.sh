#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
STILLS_DIR="$SCRIPT_DIR/stage/stills"
ANIMS_DIR="$SCRIPT_DIR/stage/anims"
COMBINED_DIR="$SCRIPT_DIR/stage/combined"
OUTPUT_DIR="$PROJECT_DIR/android/app/assets"
PACK_NAME="game"
CACHE_DIR="$SCRIPT_DIR/.cache"

GDX_VERSION="1.12.1"
TOOLS_JAR="$CACHE_DIR/gdx-tools-${GDX_VERSION}.jar"
GDX_JAR="$CACHE_DIR/gdx-${GDX_VERSION}.jar"

if [[ ! -d "$STILLS_DIR" ]]; then
  echo "ERROR: stills dir not found at $STILLS_DIR — run extract-stills.sh first" >&2
  exit 1
fi

mkdir -p "$CACHE_DIR" "$OUTPUT_DIR" "$COMBINED_DIR"

for jar_name in "gdx-tools" "gdx"; do
  jar_path="$CACHE_DIR/${jar_name}-${GDX_VERSION}.jar"
  if [[ ! -f "$jar_path" ]]; then
    echo "Downloading ${jar_name}-${GDX_VERSION}.jar from Maven Central..."
    curl -sL "https://repo1.maven.org/maven2/com/badlogicgames/gdx/${jar_name}/${GDX_VERSION}/${jar_name}-${GDX_VERSION}.jar" -o "$jar_path"
  fi
done

# Merge stills + anims into combined staging dir
rm -rf "$COMBINED_DIR"
mkdir -p "$COMBINED_DIR"
cp "$STILLS_DIR"/*.png "$COMBINED_DIR/" 2>/dev/null || true
if [[ -d "$ANIMS_DIR" ]]; then
  cp "$ANIMS_DIR"/*.png "$COMBINED_DIR/" 2>/dev/null || true
fi

png_count=$(find "$COMBINED_DIR" -maxdepth 1 -name "*.png" | wc -l | tr -d ' ')
if [[ "$png_count" -eq 0 ]]; then
  echo "ERROR: no PNGs to pack" >&2
  exit 1
fi

# TexturePacker settings JSON
SETTINGS_FILE="$SCRIPT_DIR/pack-settings.json"
cat > "$SETTINGS_FILE" << 'SETTINGS_EOF'
{
  "pot": false,
  "maxWidth": 4096,
  "maxHeight": 4096,
  "paddingX": 2,
  "paddingY": 2,
  "duplicatePadding": true,
  "stripWhitespaceX": false,
  "stripWhitespaceY": false,
  "premultiplyAlpha": false,
  "filterMin": "Linear",
  "filterMag": "Linear",
  "edgePadding": true,
  "fast": false
}
SETTINGS_EOF

# Up-to-date check: skip if atlas is newer than all source PNGs
ATLAS_FILE="$OUTPUT_DIR/${PACK_NAME}.atlas"
if [[ -f "$ATLAS_FILE" ]]; then
  needs_repack=false
  for src_dir in "$STILLS_DIR" "$ANIMS_DIR"; do
    [[ -d "$src_dir" ]] || continue
    while IFS= read -r png; do
      if [[ "$png" -nt "$ATLAS_FILE" ]]; then
        needs_repack=true
        break 2
      fi
    done < <(find "$src_dir" -maxdepth 1 -name "*.png")
  done
  if [[ "$needs_repack" == "false" ]]; then
    region_count=$(grep -c "rotate:" "$ATLAS_FILE" 2>/dev/null || echo 0)
    echo "ATLAS-PACK: up-to-date ($region_count regions), skipping."
    exit 0
  fi
fi

# Clean old atlas pages before repacking
rm -f "$OUTPUT_DIR"/${PACK_NAME}*.png "$OUTPUT_DIR"/${PACK_NAME}.atlas

echo "Packing $png_count PNGs from combined staging -> $OUTPUT_DIR/${PACK_NAME}.atlas"

java -cp "$TOOLS_JAR:$GDX_JAR" \
  com.badlogic.gdx.tools.texturepacker.TexturePacker \
  "$COMBINED_DIR" "$OUTPUT_DIR" "$PACK_NAME" "$SETTINGS_FILE"

if [[ ! -f "$ATLAS_FILE" ]]; then
  echo "ERROR: TexturePacker did not produce $ATLAS_FILE" >&2
  exit 1
fi

region_count=$(grep -c "rotate:" "$ATLAS_FILE")
page_count=$(grep -c "\.png$" "$ATLAS_FILE")
max_page=$(grep "size:" "$ATLAS_FILE" | head -1 | sed 's/.*size: //')

if grep -q "filter: Linear, Linear" "$ATLAS_FILE"; then
  echo "Filter: Linear, Linear confirmed"
else
  echo "WARN: filter not set to Linear, Linear" >&2
fi

echo "ATLAS-PACK: $region_count regions, $page_count page(s), maxPage=$max_page"
