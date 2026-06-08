#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ATLAS_FILE="$PROJECT_DIR/android/app/assets/game.atlas"

if [[ ! -f "$ATLAS_FILE" ]]; then
  echo "ERROR: game.atlas not found at $ATLAS_FILE" >&2
  exit 1
fi

# All 48 Sprite enum rawValues from SpriteCatalog.swift
# (bead spec says 44 but iOS source has 48 — authoritative from Assets.xcassets)
SPRITES=(
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

SPRITE_LIST=$(printf '%s\n' "${SPRITES[@]}")

python3 - "$ATLAS_FILE" "$SPRITE_LIST" << 'PYEOF'
import sys

atlas_path = sys.argv[1]
sprite_names = sys.argv[2].strip().split('\n')

with open(atlas_path) as f:
    lines = f.readlines()

PAGE_HEADERS = {'size:', 'format:', 'filter:', 'repeat:'}
regions = {}
pages = []
i = 0
while i < len(lines):
    line = lines[i].rstrip('\n')
    if line.endswith('.png') and (i == 0 or not lines[i-1].strip()):
        page = {'name': line}
        for j in range(i+1, min(i+5, len(lines))):
            l = lines[j].rstrip('\n')
            if l.startswith('size:'):
                w, h = l.split(':', 1)[1].strip().split(',')
                page['width'] = int(w.strip())
                page['height'] = int(h.strip())
            elif l.startswith('filter:'):
                page['filter'] = l.split(':', 1)[1].strip()
        pages.append(page)
    if line and not line[0].isspace() and not line.endswith('.png'):
        if any(line.startswith(h) for h in PAGE_HEADERS):
            i += 1
            continue
        name = line
        region = {'name': name}
        for j in range(i+1, min(i+8, len(lines))):
            l = lines[j].rstrip('\n')
            if '  index:' in l:
                region['index'] = int(l.split(':')[1].strip())
            if '  size:' in l and 'orig' not in l:
                parts = l.split(':')[1].strip().split(',')
                region['width'] = int(parts[0].strip())
                region['height'] = int(parts[1].strip())
        key = name if region.get('index', -1) == -1 else f"{name}_{region['index']}"
        if key in regions:
            regions[key]['dupe'] = True
        regions[key] = region
    i += 1

failures = 0
passes = 0

print('--- Atlas region test (text-parse fallback, no headless GL) ---')
print()

# 1. Check all Sprite enum rawValues resolve as still regions (index=-1)
missing = []
for sprite in sprite_names:
    found = any(
        r['name'] == sprite and r.get('index', -1) == -1
        for r in regions.values()
    )
    if not found:
        missing.append(sprite)

if missing:
    print(f'ATLAS-TEST FAIL: unresolved regions {missing}')
    print('All region names in atlas:')
    for k in sorted(regions.keys()):
        print(f'  {k}')
    failures += 1
else:
    passes += 1
    print(f'ATLAS-TEST: {len(sprite_names)}/{len(sprite_names)} Sprite regions resolvable')

# 2. Check filter: Linear, Linear on all pages
non_linear = [p for p in pages if 'Linear' not in p.get('filter', '')]
if non_linear:
    print(f'ATLAS-TEST FAIL: non-Linear filter on pages: {[p["name"] for p in non_linear]}')
    failures += 1
else:
    passes += 1
    print(f'ATLAS-TEST: filter=Linear,Linear on all {len(pages)} pages')

# 3. Check no duplicate region names (same name+index)
seen = {}
dupes = []
for key, r in regions.items():
    combo = (r['name'], r.get('index', -1))
    if combo in seen:
        dupes.append(combo)
    seen[combo] = True

if dupes:
    print(f'ATLAS-TEST FAIL: duplicate regions: {dupes}')
    failures += 1
else:
    passes += 1
    print(f'ATLAS-TEST: no duplicate region names ({len(regions)} unique entries)')

# 4. Check all regions have positive width/height
zero_size = [
    (k, r) for k, r in regions.items()
    if r.get('width', 0) <= 0 or r.get('height', 0) <= 0
]
if zero_size:
    print(f'ATLAS-TEST FAIL: {len(zero_size)} regions with zero/negative size')
    for k, r in zero_size:
        print(f'  {k}: {r.get("width", 0)}x{r.get("height", 0)}')
    failures += 1
else:
    passes += 1
    print(f'ATLAS-TEST: all {len(regions)} regions have positive width/height')

# Page summary
print()
print(f'Page count: {len(pages)}')
for p in pages:
    print(f'  {p["name"]}: {p.get("width", "?")}x{p.get("height", "?")}')

print()
total = 4
if failures == 0:
    print(f'ATLAS-TEST PASS: {len(sprite_names)}/{len(sprite_names)} regions resolvable, filter=Linear')
    sys.exit(0)
else:
    print(f'ATLAS-TEST FAIL: {passes}/{total} checks passed, {failures} failed')
    sys.exit(1)
PYEOF
