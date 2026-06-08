# Galaxy Monkey

Twin-stick arcade space shooter, inspired by *Ape Escape*'s "Galaxy Monkey"
minigame. Two platforms: **iOS** (SpriteKit + SwiftUI, shipping) and
**Android** (LibGDX + Kotlin, in progress).

## Status

### iOS (shipping)

- Twin-stick controls (dynamic dual joysticks, dead zones, drag).
- Player ship with lives, brief i-frames, and aim-driven auto-fire.
- Pooled projectile system, enemy spawner that homes on the player.
- Score, lives, start prompt, game-over overlay.
- Parallax starfield + optional space backdrop.
- SpriteCook art for player ship, gorilla canonical, and space backdrop.
- Normalized legacy sprites imported as fallbacks for enemies and pickups.
- ElevenLabs SFX and a 30s music loop bundled as `.caf`.
- XCUITest smoke suite (title, start, restart) — three tests, green.

### Android port (in progress)

LibGDX 1.12.1 / Kotlin 2.0.21, landscape-only, `minSdk 24`.
Mirrors iOS gameplay and feel — the iOS Swift source is the behavioral spec.

- Gradle multi-module: `:core` (Kotlin JVM game logic) + `:app` (Android shell).
- SpriteCatalog/AnimationCatalog, AudioController, HapticsController, SettingsStore.
- HUD overlays (start prompt, pause, game-over, settings with sliders + haptics toggle).
- Fixed-timestep loop, camera follow, screen shake, input facade.
- 420+ JUnit 5 headless tests via gdx-backend-headless.

## Layout

```
├── ios/                            # iOS SpriteKit game (shipping)
│   ├── project.yml                 # XcodeGen spec
│   ├── GalaxyMonkey/               # app sources + bundle resources
│   │   ├── GalaxyMonkeyApp.swift
│   │   ├── GameScene.swift         # orchestrator (behavioral spec for Android)
│   │   ├── VirtualJoystick.swift
│   │   ├── Player.swift
│   │   ├── EnemySystem.swift
│   │   ├── HUDController.swift
│   │   ├── AudioController.swift
│   │   ├── Tuning.swift
│   │   ├── Assets.xcassets/
│   │   └── Sounds/                 # .caf SFX + music
│   ├── GalaxyMonkeyUITests/
│   ├── sprite-pipeline/
│   ├── run-sim.sh
│   ├── run-device.sh
│   ├── test-smoke.sh
│   └── test-xcui.sh
│
├── android/                        # Android LibGDX/Kotlin port
│   ├── core/                       # :core — Kotlin JVM game logic
│   │   └── src/main/kotlin/dev/copt/galaxymonkey/
│   │       ├── GalaxyMonkeyGame.kt
│   │       ├── GameScreen.kt       # mirrors GameScene.swift
│   │       ├── Player.kt
│   │       ├── EnemySystem.kt
│   │       ├── HUDController.kt
│   │       ├── AudioController.kt
│   │       ├── Tuning.kt
│   │       └── SpriteCatalog.kt
│   ├── app/                        # :app — Android launcher + manifest
│   │   └── src/main/kotlin/dev/copt/galaxymonkey/
│   │       └── AndroidLauncher.kt
│   ├── app/assets/                 # game.atlas + sounds/ (OGG)
│   ├── sprite-pipeline/            # atlas packing + audio conversion
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/libs.versions.toml
│
├── AGENTS.md
├── CLAUDE.md
└── README.md
```

## Build and run

### iOS

Prerequisites: Xcode 17 / iOS 17 SDK and `xcodegen`.

```bash
brew install xcodegen
cd ios && ./run-sim.sh          # generates xcodeproj on first run
```

Force a project regeneration after editing `ios/project.yml`:

```bash
cd ios && REGEN=1 ./run-sim.sh
```

Run on a connected device:

```bash
cd ios && ./run-device.sh
```

### Android

Prerequisites: JDK 17+, Android SDK (compileSdk 34, minSdk 24).

```bash
cd android && ./gradlew :app:assembleDebug
```

Install and launch on a connected device/emulator:

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.copt.galaxymonkey/.AndroidLauncher
```

Run headless unit tests:

```bash
cd android && ./gradlew :core:test
```

Run instrumentation smoke test (requires device/emulator):

```bash
cd android && ./gradlew :app:connectedDebugAndroidTest
```

## Tests

### iOS

```bash
cd ios && ./test-smoke.sh       # build + run full UI test suite
cd ios && ./test-xcui.sh        # UI tests only
```

The XCUI suite drives the game by tapping the title prompt and using the
`#if DEBUG` accessibility hook `debugForceGameOver` to skip ahead to the
game-over overlay — same pattern as PenguinSlide.

Audio is disabled on the iOS Simulator. The simulator's coreaudio host
frequently RPC-times-out under SpriteKit's audio engine. See
`AudioController.isDisabled`.

### Android

```bash
cd android && ./gradlew :core:test    # 420+ headless JUnit 5 tests
```

## Controls

- **Left half**: drag to move. The joystick centers wherever the finger
  first lands and follows clamped to a circle. Releasing the finger
  applies exponential drag rather than an instant stop.
- **Right half**: drag to aim. The ship rotates to face the stick; bullets
  auto-fire while the stick is past the dead zone.
- Dynamic joysticks on each side — touch anywhere, not a fixed pad.

## Asset pipeline

Two flows feed the asset catalogs:

1. **Legacy normalized PNGs** under `sprite-pipeline/normalized/` ported
   from the original Android source. Imported into
   `GalaxyMonkey/Assets.xcassets` by `sprite-pipeline/import-to-xcassets.sh`.
2. **SpriteCook outputs** generated via the SpriteCook MCP. Catalogued in
   `sprite-pipeline/spritecook-assets.json` with `asset_id`, `label`,
   `role`, and `asset_path`. Recipes in `sprite-pipeline/SPRITECOOK.md`.

`SpriteCatalog.texture(for:)` returns `nil` when an imageset is missing,
so subsystems fall back to placeholder primitives — replacing art is
piecewise, never a big-bang.

## Audio pipeline

SFX and music are generated via ElevenLabs and bundled as `.caf` under
`GalaxyMonkey/Sounds/`. Manifest in `media-assets.json`. Convert MP3 → CAF
with:

```bash
afconvert -f caff -d ima4 -c 1 input.mp3 output.caf
```

IMA4 mono CAF is friendlier to the iOS Simulator's audio host than AAC.

## Phased roadmap

See `OG_PLAN.md` for the gameplay seed and `.cursor/plans/galaxy-monkey-plan_*.md`
for the full phased build plan.
