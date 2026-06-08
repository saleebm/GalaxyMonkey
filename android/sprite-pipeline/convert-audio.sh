#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SOUNDS_SRC="$PROJECT_DIR/ios/GalaxyMonkey/Sounds"
SOUNDS_DST="$PROJECT_DIR/android/app/assets/sounds"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

if [[ ! -d "$SOUNDS_SRC" ]]; then
  echo "ERROR: iOS Sounds dir not found at $SOUNDS_SRC" >&2
  exit 1
fi

if ! command -v ffmpeg &>/dev/null; then
  echo "ERROR: ffmpeg not found. Install via: brew install ffmpeg" >&2
  exit 1
fi

if ! command -v afconvert &>/dev/null; then
  echo "ERROR: afconvert not found (requires macOS)" >&2
  exit 1
fi

mkdir -p "$SOUNDS_DST"

EXPECTED=(bg_music enemy_shot explosion game_over player_hit shot ui_tap warp)

converted=0
skipped=0
total=${#EXPECTED[@]}

for name in "${EXPECTED[@]}"; do
  src="$SOUNDS_SRC/${name}.caf"
  dst="$SOUNDS_DST/${name}.ogg"

  if [[ ! -f "$src" ]]; then
    echo "ERROR: missing source: $src" >&2
    exit 1
  fi

  if [[ -f "$dst" && "$dst" -nt "$src" ]]; then
    echo "SKIP $name.caf (up-to-date)"
    skipped=$((skipped + 1))
    converted=$((converted + 1))
    continue
  fi

  # CAF IMA4 -> WAV PCM (macOS native), then WAV -> OGG Opus (ffmpeg)
  wav="$TMP_DIR/${name}.wav"
  afconvert "$src" -d LEI16 -f WAVE "$wav"
  ffmpeg -y -i "$wav" -c:a libopus -b:a 128k "$dst" 2>/dev/null
  bytes=$(stat -f%z "$dst" 2>/dev/null || stat --printf="%s" "$dst" 2>/dev/null)
  echo "AUDIO-CONVERT ${name}.caf -> ${name}.ogg (${bytes} bytes)"
  converted=$((converted + 1))
done

echo ""
echo "${converted}/${total} converted"

# Validate all outputs
echo ""
echo "--- Validation ---"
errors=0
for name in "${EXPECTED[@]}"; do
  dst="$SOUNDS_DST/${name}.ogg"
  if [[ ! -f "$dst" ]]; then
    echo "FAIL: $name.ogg missing" >&2
    errors=$((errors + 1))
    continue
  fi

  size=$(stat -f%z "$dst" 2>/dev/null || stat --printf="%s" "$dst" 2>/dev/null)
  if [[ "$size" -eq 0 ]]; then
    echo "FAIL: $name.ogg is empty" >&2
    errors=$((errors + 1))
    continue
  fi

  magic=$(xxd -l4 -p "$dst")
  if [[ "$magic" != "4f676753" ]]; then
    echo "FAIL: $name.ogg does not start with OggS magic (got $magic)" >&2
    errors=$((errors + 1))
    continue
  fi

  echo "OK: $name.ogg ($size bytes, OggS verified)"
done

if [[ $errors -gt 0 ]]; then
  echo "FAIL: $errors files failed validation" >&2
  exit 1
fi

echo ""
echo "All $total .ogg files valid."
