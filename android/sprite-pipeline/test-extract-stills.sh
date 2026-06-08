#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STAGE_DIR="$SCRIPT_DIR/stage/stills"

# All 48 Sprite enum rawValues from SpriteCatalog.swift
EXPECTED=(
  AstroSniperLeft AstroSniperRight Bomb Bullet Callisto
  DroneSwarmLeft DroneSwarmRight Earth EnemyBullet Europa
  Explosion Ganymede Glow GoldenBanana GorillaLeft
  GorillaRight GorillaWindup HeavyCosmonautLeft HeavyCosmonautRight
  Io Jupiter LifeHeart Luna Mars Mercury
  MiniBossLeft MiniBossRight Neptune PauseIcon PlasmaJelly
  Player PreppyLeft PreppyRight Rock01 Rock02 Rock03 Rock04
  Saturn ShadyLeft ShadyRight SpaceBackdrop Sun Titan
  Title Uranus Venus WhiteLeft WhiteRight
)

PNG_MAGIC="89504e470d0a1a0a"
JPEG_MAGIC_PREFIX="ffd8ff"
failures=0

echo "--- Still PNG extraction test ---"

# Check for expected files
missing=()
corrupt=()
for name in "${EXPECTED[@]}"; do
  f="$STAGE_DIR/${name}.png"
  if [[ ! -f "$f" ]]; then
    missing+=("$name")
    continue
  fi
  size=$(stat -f%z "$f" 2>/dev/null || stat --printf="%s" "$f" 2>/dev/null)
  if [[ "$size" -eq 0 ]]; then
    corrupt+=("$name (empty)")
    continue
  fi
  magic=$(xxd -l8 -p "$f")
  if [[ "$magic" != "$PNG_MAGIC" && "${magic:0:6}" != "$JPEG_MAGIC_PREFIX" ]]; then
    corrupt+=("$name (bad magic: $magic)")
  fi
done

# Check for extra files (not in expected list)
extra=()
if [[ -d "$STAGE_DIR" ]]; then
  while IFS= read -r f; do
    base=$(basename "$f" .png)
    found=false
    for name in "${EXPECTED[@]}"; do
      if [[ "$base" == "$name" ]]; then found=true; break; fi
    done
    if [[ "$found" == "false" ]]; then
      extra+=("$base")
    fi
  done < <(find "$STAGE_DIR" -maxdepth 1 -name "*.png" -type f)
fi

# Check no AppIcon/AccentColor leaked
for leak in AppIcon AccentColor; do
  if [[ -f "$STAGE_DIR/${leak}.png" ]]; then
    extra+=("$leak (LEAKED)")
  fi
done

# Report
actual_count=$(find "$STAGE_DIR" -maxdepth 1 -name "*.png" -type f 2>/dev/null | wc -l | tr -d ' ')
echo "Found: $actual_count PNGs in stage/stills/"
echo "Expected: ${#EXPECTED[@]} Sprite enum rawValues"
echo ""

echo "Sorted basenames found:"
find "$STAGE_DIR" -maxdepth 1 -name "*.png" -type f -exec basename {} .png \; | sort
echo ""

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "MISSING FROM STAGE: ${missing[*]}"
  failures=$((failures + 1))
fi
if [[ ${#extra[@]} -gt 0 ]]; then
  echo "EXTRA IN STAGE: ${extra[*]}"
  failures=$((failures + 1))
fi
if [[ ${#corrupt[@]} -gt 0 ]]; then
  echo "CORRUPT/EMPTY: ${corrupt[*]}"
  failures=$((failures + 1))
fi

if [[ $failures -eq 0 ]]; then
  echo "PNG-EXTRACT-TEST PASS: ${#EXPECTED[@]}/${#EXPECTED[@]} stills, all enum-aligned"
  exit 0
else
  echo "PNG-EXTRACT-TEST FAIL"
  exit 1
fi
