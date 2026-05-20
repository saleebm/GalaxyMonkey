# 00 · Bootstrapping Galaxy Monkey

## Hook

Galaxy Monkey is a fresh iOS SpriteKit twin-stick shooter, bootstrapped
from the PenguinSlide project shape and the legacy Android *Galaxy Monkey*
asset bundle.

## Decision

Reuse PenguinSlide's working pieces verbatim:

- XcodeGen `project.yml` shape (app target + UI test target + scheme).
- SwiftUI `SpriteView` host wrapping a long-lived `GameScene` via `@State`.
- Subsystem-per-file layout (`Player`, `EnemySystem`, `ProjectileSystem`,
  `HUDController`, `AudioController`, `Tuning`, `SpriteCatalog`).
- `Assets.xcassets` for art, bundled `Sounds/` for `.caf` audio.
- `run-sim.sh` / `run-device.sh` / `test-xcui.sh` triple.
- The `#if DEBUG` `SKLabelNode("debugForceGameOver")` accessibility hook so
  XCUITest can short-circuit gameplay.

Diverge on the parts that change with twin-stick:

- Two dynamic `VirtualJoystick` nodes mounted on the camera, instead of
  tilt + keyboard input.
- Per-touch joystick anchoring (the center is wherever the finger first
  lands on that half of the screen).
- Pooled `ProjectileSystem` instead of falling-icicle physics.
- Spawning enemies just outside the visible bounds with simple homing
  velocity, instead of vertically-aimed icicles with manual gravity.

## Phases

1. Xcode shell + run scripts + assets stub.
2. Placeholder twin-stick prototype with primitive shapes.
3. Asset pipeline: import normalized legacy PNGs and generate SpriteCook
   replacements piecewise — `SpriteCatalog` falls back to placeholders
   when an imageset is absent.
4. Audio pipeline: generate SFX + a 30s music loop with ElevenLabs,
   convert MP3 → IMA4 CAF, bundle.
5. Tune feel, add waves, smoke tests, blog posts.

## Trade-offs

- **Placeholder-first vs asset-first.** Drawing a `SKShapeNode` hexagon
  for the player before any art exists lets the controls land before the
  game becomes "the game with the bad placeholder ship". Replacing one
  imageset at a time is friction-free because `SpriteCatalog` short-circuits
  on `UIImage(named:)` returning nil.
- **AAC vs IMA4 CAF.** AAC is half the size but the iOS Simulator's
  coreaudio host is unreliable with AAC-in-CAF on cold boot. IMA4 mono
  CAF avoids the timeout entirely.
- **Audio off on simulator.** SpriteKit's audio engine RPC-times-out
  against the simulator's coreaudio host often enough to be untestable.
  Real devices are fine. `AudioController.isDisabled` makes this explicit.

## Generalization

When porting another retro game to SpriteKit:

- Lock the project skeleton from a known-good neighbor project before
  writing any gameplay code; you'll iterate faster.
- Put every "where does this number live" question into `Tuning` from day
  one — feel changes shouldn't require git grep.
- Make every catalog (sprites, sounds, art manifest) graceful about
  missing entries so generation can lag implementation.
