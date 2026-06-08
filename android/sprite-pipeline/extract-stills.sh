#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
XCASSETS="$PROJECT_DIR/ios/GalaxyMonkey/Assets.xcassets"
STAGE_DIR="$SCRIPT_DIR/stage/stills"

if [[ ! -d "$XCASSETS" ]]; then
  echo "ERROR: Assets.xcassets not found at $XCASSETS" >&2
  exit 1
fi

if ! command -v python3 &>/dev/null; then
  echo "ERROR: python3 required for JSON parsing" >&2
  exit 1
fi

mkdir -p "$STAGE_DIR"

count=0
errors=0

for imageset_dir in "$XCASSETS"/*.imageset; do
  name="$(basename "$imageset_dir" .imageset)"

  # Skip non-game assets
  if [[ "$name" == "AppIcon" || "$name" == "AccentColor" ]]; then
    continue
  fi

  contents="$imageset_dir/Contents.json"
  if [[ ! -f "$contents" ]]; then
    echo "WARN: no Contents.json in $name.imageset" >&2
    errors=$((errors + 1))
    continue
  fi

  # Extract the 1x filename from Contents.json
  filename=$(python3 -c "
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
for img in data.get('images', []):
    if img.get('scale') == '1x' and img.get('filename'):
        print(img['filename'])
        break
" "$contents")

  if [[ -z "$filename" ]]; then
    echo "WARN: no 1x filename in $name.imageset/Contents.json" >&2
    errors=$((errors + 1))
    continue
  fi

  src="$imageset_dir/$filename"
  dst="$STAGE_DIR/${name}.png"

  if [[ ! -f "$src" ]]; then
    echo "WARN: source PNG missing: $src" >&2
    errors=$((errors + 1))
    continue
  fi

  cp "$src" "$dst"
  echo "EXTRACT $name -> stills/${name}.png"
  count=$((count + 1))
done

echo ""
echo "--- Summary ---"
echo "$count/$((count + errors)) extracted to $STAGE_DIR"

if [[ $errors -gt 0 ]]; then
  echo "WARN: $errors imagesets had issues (see above)" >&2
fi

# Cross-check against Sprite enum rawValues
SPRITE_CATALOG="$PROJECT_DIR/ios/GalaxyMonkey/SpriteCatalog.swift"
if [[ -f "$SPRITE_CATALOG" ]]; then
  echo ""
  echo "--- Enum cross-check ---"
  enum_names=$(grep -oE '= "[A-Za-z0-9]+"' "$SPRITE_CATALOG" | sed 's/= "//;s/"//')
  disk_names=$(ls "$STAGE_DIR" 2>/dev/null | sed 's/\.png$//')

  missing_on_disk=0
  for e in $enum_names; do
    if [[ ! -f "$STAGE_DIR/${e}.png" ]]; then
      echo "MISS: Sprite enum '$e' has no extracted PNG"
      missing_on_disk=$((missing_on_disk + 1))
    fi
  done

  extra_on_disk=0
  for d in $disk_names; do
    if ! echo "$enum_names" | grep -qx "$d"; then
      echo "EXTRA: '$d.png' has no matching Sprite enum case"
      extra_on_disk=$((extra_on_disk + 1))
    fi
  done

  if [[ $missing_on_disk -eq 0 && $extra_on_disk -eq 0 ]]; then
    echo "All extracted PNGs match Sprite enum cases exactly."
  fi
fi
