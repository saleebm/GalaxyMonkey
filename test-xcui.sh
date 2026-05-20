#!/usr/bin/env bash
# Run only the UI test target.

set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="$PROJECT_DIR/GalaxyMonkey.xcodeproj"
BUILD_DIR=/tmp/GalaxyMonkeyBuild

if [[ ! -d "$PROJECT" ]]; then
  command -v xcodegen >/dev/null || { echo "xcodegen not installed: brew install xcodegen"; exit 1; }
  (cd "$PROJECT_DIR" && xcodegen generate)
fi

DEVICE_ID=$(xcrun simctl list devices booted | grep -oE '[0-9A-F-]{36}' | head -n1 || true)
: "${DEVICE_ID:=$(xcrun simctl list devices available | grep -E '    iPhone ' | grep -oE '[0-9A-F-]{36}' | head -n1 || true)}"
[[ -n "$DEVICE_ID" ]] || { echo "No iPhone simulator available."; exit 1; }

xcrun simctl bootstatus "$DEVICE_ID" -b >/dev/null 2>&1 || xcrun simctl boot "$DEVICE_ID" 2>/dev/null || true

xcodebuild \
  -project "$PROJECT" \
  -scheme GalaxyMonkey \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -only-testing:GalaxyMonkeyUITests \
  -derivedDataPath "$BUILD_DIR/dd" \
  test | tail -60
