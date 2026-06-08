#!/usr/bin/env bash
# Build, install, and launch GalaxyMonkey on a connected iOS device.
#
# Usage:
#   ./run-device.sh                          # auto-pick first connected device
#   DEVICE_ID=<udid> ./run-device.sh         # explicit device
#   REGEN=1 ./run-device.sh                  # re-run xcodegen first

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

DEVICE_ID="${DEVICE_ID:-}"
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID=$(xcrun xctrace list devices 2>&1 | grep -E '\(.+\) \([0-9A-F-]+\)$' | grep -v Simulator | head -n1 | sed -E 's/.*\(([0-9A-F-]+)\)$/\1/')
fi
[[ -n "$DEVICE_ID" ]] || { echo "No connected iOS device found. Plug one in or set DEVICE_ID."; exit 1; }
echo "→ Device: $DEVICE_ID"

xcodebuild \
  -project "$PROJECT" \
  -target "$TARGET" \
  -sdk iphoneos \
  -configuration Debug \
  -destination "platform=iOS,id=$DEVICE_ID" \
  SYMROOT="$BUILD_DIR" \
  build | tail -1

APP="$BUILD_DIR/Debug-iphoneos/$TARGET.app"
[[ -d "$APP" ]] || { echo "Build did not produce $APP"; exit 1; }

xcrun devicectl device install app --device "$DEVICE_ID" "$APP"
xcrun devicectl device process launch --device "$DEVICE_ID" "$BUNDLE_ID"
