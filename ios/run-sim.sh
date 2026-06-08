#!/usr/bin/env bash
# Build, install, and launch GalaxyMonkey on an iOS Simulator.
#
# Why target-mode build (not -scheme): with Xcode 26 mid-install of iOS
# platform support, scheme-based destination resolution emits "Supported
# platforms for the buildables in the current scheme is empty" and refuses
# every destination. Target-mode + -sdk iphonesimulator bypasses that path.
#
# Usage:
#   ./run-sim.sh                       # default sim (booted iPhone, else iPhone 17 Pro)
#   SIM_DEVICE_ID=<udid> ./run-sim.sh  # explicit sim
#   REGEN=1 ./run-sim.sh               # re-run xcodegen first (use after editing project.yml)

set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="$PROJECT_DIR/GalaxyMonkey.xcodeproj"
TARGET=GalaxyMonkey
BUNDLE_ID=dev.copt.GalaxyMonkey
BUILD_DIR=/tmp/GalaxyMonkeyBuild

if [[ "${REGEN:-0}" == "1" ]] || [[ ! -d "$PROJECT" ]]; then
  command -v xcodegen >/dev/null || { echo "xcodegen not installed: brew install xcodegen"; exit 1; }
  (cd "$PROJECT_DIR" && xcodegen generate)
fi

DEVICE_ID="${SIM_DEVICE_ID:-}"
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID=$(xcrun simctl list devices booted | grep -oE '[0-9A-F-]{36}' | head -n1 || true)
  : "${DEVICE_ID:=$(xcrun simctl list devices available | grep -E 'iPhone 17 Pro \(' | grep -oE '[0-9A-F-]{36}' | head -n1 || true)}"
  : "${DEVICE_ID:=$(xcrun simctl list devices available | grep -E '    iPhone ' | grep -oE '[0-9A-F-]{36}' | head -n1 || true)}"
fi
[[ -n "$DEVICE_ID" ]] || { echo "No iPhone simulator found. Create one in Xcode or set SIM_DEVICE_ID."; exit 1; }
echo "→ Simulator: $DEVICE_ID"

xcrun simctl bootstatus "$DEVICE_ID" -b >/dev/null 2>&1 || xcrun simctl boot "$DEVICE_ID" 2>/dev/null || true
open -a Simulator

xcodebuild \
  -project "$PROJECT" \
  -target "$TARGET" \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  ARCHS=arm64 \
  SYMROOT="$BUILD_DIR" \
  build | tail -1

APP="$BUILD_DIR/Debug-iphonesimulator/$TARGET.app"
[[ -d "$APP" ]] || { echo "Build did not produce $APP"; exit 1; }

xcrun simctl install "$DEVICE_ID" "$APP"
xcrun simctl launch "$DEVICE_ID" "$BUNDLE_ID"

cat <<'EOF'

Running. Tap to start. Left half = move, right half = aim and fire.
EOF
