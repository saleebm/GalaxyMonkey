#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ATLAS_FILE="$PROJECT_DIR/android/app/assets/game.atlas"

if [[ ! -f "$ATLAS_FILE" ]]; then
  echo "ERROR: game.atlas not found at $ATLAS_FILE" >&2
  exit 1
fi

# 31 mapped AnimationSet bases with expected frame counts
# Derived from AnimationSet enum in SpriteCatalog.swift
declare -A EXPECTED=(
  [gorilla_left_idle]=7
  [gorilla_right_idle]=7
  [gorilla_left_windup]=7
  [gorilla_right_windup]=7
  [bomb_explosion]=25
  [drone_swarm_left_idle]=7
  [drone_swarm_right_idle]=7
  [plasma_jelly_idle]=7
  [preppy_left_idle]=7
  [preppy_right_idle]=7
  [white_left_idle]=7
  [white_right_idle]=7
  [shady_left_idle]=7
  [shady_right_idle]=7
  [heavy_cosmonaut_left_idle]=7
  [heavy_cosmonaut_right_idle]=7
  [astro_sniper_left_idle]=7
  [astro_sniper_right_idle]=7
  [mini_boss_left_idle]=7
  [mini_boss_right_idle]=7
  [preppy_left_walk]=7
  [preppy_right_walk]=7
  [white_left_walk]=7
  [white_right_walk]=7
  [shady_left_walk]=7
  [shady_right_walk]=7
  [heavy_cosmonaut_left_walk]=7
  [heavy_cosmonaut_right_walk]=7
  [astro_sniper_left_walk]=7
  [astro_sniper_right_walk]=7
  [mini_boss_walk]=7
)

# 2 orphan atlases (not in AnimationSet enum)
declare -A ORPHANS=(
  [player_spin]=7
  [explosion]=25
)

# Parse atlas: extract region name -> sorted indices
python3 - "$ATLAS_FILE" << 'PYEOF' > /tmp/anim_test_results.txt
import sys

atlas_path = sys.argv[1]
with open(atlas_path) as f:
    lines = f.readlines()

PAGE_HEADERS = {'size:', 'format:', 'filter:', 'repeat:'}
regions = {}
i = 0
while i < len(lines):
    line = lines[i].rstrip('\n')
    if line and not line[0].isspace() and not line.endswith('.png'):
        if any(line.startswith(h) for h in PAGE_HEADERS):
            i += 1
            continue
        name = line
        for j in range(i+1, min(i+8, len(lines))):
            if '  index:' in lines[j]:
                idx = int(lines[j].split(':')[1].strip())
                if idx >= 0:
                    regions.setdefault(name, []).append(idx)
                break
    i += 1

for name in sorted(regions.keys()):
    indices = sorted(regions[name])
    print(f"{name}:{len(indices)}:{','.join(str(x) for x in indices)}")
PYEOF

echo "--- Animation atlas test ---"
passes=0
failures=0

for base in "${!EXPECTED[@]}"; do
  exp=${EXPECTED[$base]}
  line=$(grep "^${base}:" /tmp/anim_test_results.txt || echo "")

  if [[ -z "$line" ]]; then
    echo "ANIM-TEST FAIL $base: expected $exp got 0, not found in atlas"
    failures=$((failures + 1))
    continue
  fi

  count=$(echo "$line" | cut -d: -f2)
  indices_str=$(echo "$line" | cut -d: -f3)

  # Check count
  if [[ "$count" -ne "$exp" ]]; then
    echo "ANIM-TEST FAIL $base: expected $exp got $count, indices=[$indices_str]"
    failures=$((failures + 1))
    continue
  fi

  # Check contiguity
  IFS=',' read -ra idx_arr <<< "$indices_str"
  contiguous=true
  for ((j=1; j<${#idx_arr[@]}; j++)); do
    if [[ $((idx_arr[j])) -ne $((idx_arr[j-1] + 1)) ]]; then
      contiguous=false
      break
    fi
  done

  if [[ "$contiguous" == "false" ]]; then
    echo "ANIM-TEST FAIL $base: $count frames but indices not contiguous [$indices_str]"
    failures=$((failures + 1))
    continue
  fi

  echo "ANIM-TEST $base: got $count/$exp frames, order OK"
  passes=$((passes + 1))
done

echo ""
echo "--- Orphan atlas check ---"
for base in "${!ORPHANS[@]}"; do
  exp=${ORPHANS[$base]}
  line=$(grep "^${base}:" /tmp/anim_test_results.txt || echo "")
  if [[ -n "$line" ]]; then
    count=$(echo "$line" | cut -d: -f2)
    echo "ORPHAN $base: $count frames present (no collision with mapped bases)"
  else
    echo "ORPHAN $base: not found in atlas (OK, unmapped)"
  fi
done

echo ""
total=${#EXPECTED[@]}
if [[ $failures -eq 0 ]]; then
  echo "ANIM-TEST: $passes/$total animations valid"
  exit 0
else
  echo "ANIM-TEST FAIL: $passes/$total passed, $failures failed"
  exit 1
fi
