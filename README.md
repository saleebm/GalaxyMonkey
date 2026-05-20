# Galaxy Monkey

Twin-stick arcade space shooter for iOS, inspired by *Ape Escape*'s
"Galaxy Monkey" minigame. SpriteKit + SwiftUI, iOS 17+.

## Status

Phase 1 prototype complete:

- Twin-stick controls (dynamic dual joysticks, dead zones, drag).
- Player ship with lives, brief i-frames, and aim-driven auto-fire.
- Pooled projectile system, enemy spawner that homes on the player.
- Score, lives, start prompt, game-over overlay.
- Parallax starfield + optional space backdrop.
- SpriteCook art for player ship, gorilla canonical, and space backdrop.
- Normalized legacy sprites imported as fallbacks for enemies and pickups.
- ElevenLabs SFX (shot, enemy_shot, explosion, hit, game_over, ui_tap) and
  a 30s music loop, all bundled as `.caf` under `GalaxyMonkey/Sounds/`.
- XCUITest smoke suite (title, start, restart) — three tests, green.

## Layout

```
GalaxyMonkey/
├── project.yml                 # XcodeGen spec
├── GalaxyMonkey/               # app sources + bundle resources
│   ├── GalaxyMonkeyApp.swift
│   ├── ContentView.swift       # SwiftUI host for SpriteView
│   ├── GameScene.swift         # orchestrator
│   ├── VirtualJoystick.swift   # dynamic dual-stick input
│   ├── Player.swift            # ship: movement, aim, HP, invulnerability
│   ├── ProjectileSystem.swift  # pooled bullets (player + enemy)
│   ├── EnemySystem.swift       # spawn + homing
│   ├── HUDController.swift     # score, lives, overlays
│   ├── Starfield.swift         # parallax stars
│   ├── SpriteCatalog.swift     # Assets.xcassets loader
│   ├── AudioController.swift   # music + SFX
│   ├── Tuning.swift            # gameplay constants
│   ├── Assets.xcassets/        # imagesets (Player, Gorilla, enemies, …)
│   ├── Sounds/                 # .caf SFX + music loop
│   ├── Info.plist
│   ├── PrivacyInfo.xcprivacy
│   └── LaunchScreen.storyboard
├── GalaxyMonkeyUITests/        # XCUITest smoke suite
├── sprite-pipeline/            # SpriteCook + legacy-port tooling
│   ├── normalized/             # legacy PNGs (gitignored after first port)
│   ├── spritecook-assets.json  # manifest of SC-generated assets
│   ├── import-to-xcassets.sh   # normalized → Assets.xcassets
│   └── SPRITECOOK.md           # generation recipes
├── media-assets.json           # manifest of bundled audio
├── run-sim.sh                  # build + install + launch on Simulator
├── run-device.sh               # build + install + launch on device
├── test-smoke.sh               # full xcodebuild test
└── test-xcui.sh                # only the UI test target
```

## Build and run

Prerequisites: Xcode 17 / iOS 17 SDK and `xcodegen`.

```bash
brew install xcodegen
./run-sim.sh                # generates xcodeproj on first run
```

Force a project regeneration after editing `project.yml`:

```bash
REGEN=1 ./run-sim.sh
```

Run on a connected device (requires automatic signing with the team in
`project.yml`):

```bash
./run-device.sh
```

## Tests

```bash
./test-smoke.sh             # build + run full UI test suite
./test-xcui.sh              # UI tests only
```

The XCUI suite drives the game by tapping the title prompt and using the
`#if DEBUG` accessibility hook `debugForceGameOver` to skip ahead to the
game-over overlay — same pattern as PenguinSlide.

Audio is disabled on the iOS Simulator. The simulator's coreaudio host
frequently RPC-times-out under SpriteKit's audio engine. See
`AudioController.isDisabled`.

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
