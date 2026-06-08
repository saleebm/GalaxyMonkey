#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
XCASSETS="$PROJECT_DIR/ios/GalaxyMonkey/Assets.xcassets"
STAGE_DIR="$SCRIPT_DIR/stage/anims"

if [[ ! -d "$XCASSETS" ]]; then
  echo "ERROR: Assets.xcassets not found at $XCASSETS" >&2
  exit 1
fi

mkdir -p "$STAGE_DIR"

atlas_count=0
frame_count=0
errors=0

pascal_to_snake() {
  python3 -c "
import re, sys
s = sys.argv[1]
if s.endswith('Anim'): s = s[:-4]
print(re.sub(r'(?<=[a-z0-9])([A-Z])', r'_\1', s).lower())
" "$1"
}

for atlas_dir in "$XCASSETS"/*.spriteatlas; do
  atlas_name="$(basename "$atlas_dir" .spriteatlas)"
  base=$(pascal_to_snake "$atlas_name")
  local_frames=0
  first_idx=""
  last_idx=""

  # Collect imagesets sorted by name
  while IFS= read -r imageset_dir; do
    [[ -d "$imageset_dir" ]] || continue
    frame_name="$(basename "$imageset_dir" .imageset)"
    contents="$imageset_dir/Contents.json"

    if [[ ! -f "$contents" ]]; then
      echo "WARN: no Contents.json in $atlas_name/$frame_name.imageset" >&2
      errors=$((errors + 1))
      continue
    fi

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
      continue
    fi

    src="$imageset_dir/$filename"
    # Extract the numeric index from the original frame name
    idx=$(echo "$frame_name" | grep -oE '[0-9]+$' || echo "")
    if [[ -z "$idx" ]]; then
      echo "WARN: no numeric suffix in $frame_name" >&2
      errors=$((errors + 1))
      continue
    fi

    # Name as <atlas_base>_<idx>.png so TexturePacker keys by atlas_base
    dst="$STAGE_DIR/${base}_${idx}.png"

    if [[ ! -f "$src" ]]; then
      echo "WARN: source PNG missing: $src" >&2
      errors=$((errors + 1))
      continue
    fi

    cp "$src" "$dst"
    local_frames=$((local_frames + 1))
    frame_count=$((frame_count + 1))

    if [[ -z "$first_idx" ]]; then
      first_idx="$idx"
    fi
    last_idx="$idx"
  done < <(find "$atlas_dir" -maxdepth 1 -name "*.imageset" -type d | sort)

  if [[ $local_frames -gt 0 ]]; then
    echo "ANIM-EXTRACT: $atlas_name -> $local_frames frames ($base _${first_idx}..${last_idx})"
    atlas_count=$((atlas_count + 1))
  fi
done

echo ""
echo "--- Summary ---"
echo "$atlas_count animation sets, $frame_count total frames extracted to $STAGE_DIR"
if [[ $errors -gt 0 ]]; then
  echo "WARN: $errors frames had issues" >&2
fi
