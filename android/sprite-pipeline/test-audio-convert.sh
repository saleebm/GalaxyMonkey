#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SOUNDS_DIR="$PROJECT_DIR/android/app/assets/sounds"

EXPECTED=(bg_music enemy_shot explosion game_over player_hit shot ui_tap warp)
OGG_MAGIC="4f676753"

failures=0
echo "--- Audio conversion test ---"

missing=()
invalid=()
max_size=0
max_name=""

for name in "${EXPECTED[@]}"; do
  f="$SOUNDS_DIR/${name}.ogg"
  if [[ ! -f "$f" ]]; then
    missing+=("$name")
    continue
  fi

  size=$(stat -f%z "$f" 2>/dev/null || stat --printf="%s" "$f" 2>/dev/null)
  if [[ "$size" -eq 0 ]]; then
    invalid+=("$name (empty)")
    continue
  fi

  magic=$(xxd -l4 -p "$f")
  if [[ "$magic" != "$OGG_MAGIC" ]]; then
    invalid+=("$name (bad magic: $magic)")
    continue
  fi

  # Probe codec/duration if ffprobe available
  probe=""
  if command -v ffprobe &>/dev/null; then
    codec=$(ffprobe -v quiet -select_streams a:0 -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 "$f" 2>/dev/null || echo "?")
    dur=$(ffprobe -v quiet -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$f" 2>/dev/null || echo "?")
    probe=" codec=$codec dur=${dur}s"
  fi

  echo "AUDIO-TEST ${name}: magic=OggS bytes=${size}${probe}"

  if [[ $size -gt $max_size ]]; then
    max_size=$size
    max_name=$name
  fi
done

echo ""

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "AUDIO-TEST FAIL: missing=[${missing[*]}]"
  failures=$((failures + 1))
fi
if [[ ${#invalid[@]} -gt 0 ]]; then
  echo "AUDIO-TEST FAIL: invalid=[${invalid[*]}]"
  failures=$((failures + 1))
fi

# Sanity: bg_music should be the largest file
if [[ "$max_name" != "bg_music" && ${#missing[@]} -eq 0 ]]; then
  echo "AUDIO-TEST WARN: largest file is $max_name ($max_size), expected bg_music"
  failures=$((failures + 1))
fi

if [[ $failures -eq 0 ]]; then
  echo "AUDIO-TEST PASS: 8/8 ogg valid"
  exit 0
else
  echo "AUDIO-TEST FAIL"
  exit 1
fi
