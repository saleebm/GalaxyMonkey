#!/bin/bash
# Add L/R variants for drone swarm + gorilla via pixel-perfect sips horizontal
# flips (no SpriteCook generation). Per spritecook-mirror-lesson: flip, don't
# prompt-mirror. Established Preppy*Left/Right convention: flipped atlases get
# distinct internal frame names so the compiled catalog has no name collisions.
#
#   drone  : current art faces LEFT  -> keep as Left, flip -> Right
#   gorilla: current art faces RIGHT -> keep as Right, flip -> Left
set -euo pipefail
cd "$(dirname "$0")/../GalaxyMonkey/Assets.xcassets"

# flip_atlas SRC DST OLD_INFIX NEW_INFIX
# Copies an atlas, renames each frame imageset+png (OLD_INFIX->NEW_INFIX),
# rewrites the per-frame Contents.json filename, and horizontally flips the png.
flip_atlas() {
  local src="$1" dst="$2" old="$3" new="$4"
  cp -R "$src" "$dst"
  for d in "$dst"/*.imageset; do
    local base newbase oldpng newpng
    base="$(basename "$d" .imageset)"        # e.g. drone_swarm_idle_01
    newbase="${base/$old/$new}"              # e.g. drone_swarm_right_idle_01
    oldpng="$d/$base.png"
    sips -f horizontal "$oldpng" --out "$oldpng" >/dev/null
    sed -i '' "s/$base\.png/$newbase.png/" "$d/Contents.json"
    mv "$oldpng" "$d/$newbase.png"
    mv "$d" "$(dirname "$d")/$newbase.imageset"
  done
}

# flip_imageset SRC DST OLDPNG NEWPNG  (single still)
flip_imageset() {
  local src="$1" dst="$2" oldpng="$3" newpng="$4"
  cp -R "$src" "$dst"
  sips -f horizontal "$dst/$oldpng" --out "$dst/$newpng" >/dev/null
  [ "$oldpng" != "$newpng" ] && rm "$dst/$oldpng" && sed -i '' "s/$oldpng/$newpng/" "$dst/Contents.json"
  true
}

### DRONE — current = Left, create Right flip ###
mv DroneSwarm.imageset            DroneSwarmLeft.imageset
mv DroneSwarmIdleAnim.spriteatlas DroneSwarmLeftIdleAnim.spriteatlas
flip_imageset DroneSwarmLeft.imageset DroneSwarmRight.imageset drone_swarm_sc.png drone_swarm_right_sc.png
flip_atlas DroneSwarmLeftIdleAnim.spriteatlas DroneSwarmRightIdleAnim.spriteatlas drone_swarm_idle drone_swarm_right_idle

### GORILLA — current = Right, create Left flip ###
mv Gorilla.imageset                 GorillaRight.imageset
mv GorillaIdleAnim.spriteatlas      GorillaRightIdleAnim.spriteatlas
mv GorillaWindupAnim.spriteatlas    GorillaRightWindupAnim.spriteatlas
flip_imageset GorillaRight.imageset GorillaLeft.imageset gorilla_sc.png gorilla_left_sc.png
flip_atlas GorillaRightIdleAnim.spriteatlas   GorillaLeftIdleAnim.spriteatlas   gorilla_idle   gorilla_left_idle
flip_atlas GorillaRightWindupAnim.spriteatlas GorillaLeftWindupAnim.spriteatlas gorilla_windup gorilla_left_windup

echo "DONE"
