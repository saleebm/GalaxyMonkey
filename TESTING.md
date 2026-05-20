# Testing Galaxy Monkey

## Quick gates

| Command | What it does |
|--|--|
| `./test-xcui.sh` | UI test target only — fastest signal |
| `./test-smoke.sh` | Full `xcodebuild test` build + UI tests |
| `./run-sim.sh` | Build + install + launch on the iOS Simulator |
| `./run-device.sh` | Build + install + launch on a connected device |

## XCUITest fixture

`GalaxyMonkeyUITests/GalaxyMonkeyUITests.swift` covers:

1. `testTitleScreenAppears` — title, sub-prompt, and Tap-to-start visible.
2. `testStartTriggersGameAndShowsGameOver` — tap to start, force game-over
   via the `#if DEBUG` accessibility hook, assert overlay appears.
3. `testRestartReturnsToPlayableState` — same flow then tap to restart,
   assert game-over overlay clears.

The debug hook lives in `GameScene.installDebugForceGameOver()`. It
installs a tiny red `SKLabelNode` named `debugForceGameOver` in the
top-left corner. SpriteKit only surfaces `SKLabelNode` to XCUITest through
the automatic accessibility traversal, so other node types would be
silently pruned.

## Audio on the simulator

Audio is disabled when `targetEnvironment(simulator)` is true and when the
`XCTestConfigurationFilePath` env var is set (XCUITest). The simulator's
coreaudio host RPC-times-out under SpriteKit's audio engine, manifesting
as a `SIGABRT` inside `AURemoteIO::Cleanup()`. Real devices play audio
normally.

If you need to test audio on the simulator, flip `AudioController.isDisabled`
to `false` and erase + reboot the simulator first.

## Dogfooding

Manual passes for the next session:

- Joystick deadzone feel — adjust `Tuning.Joystick.deadZone` if drift.
- Fire cadence — `Tuning.Projectile.fireInterval` (currently 0.15s).
- Enemy spawn pacing — `Tuning.Enemy.spawnIntervalStart` / `…End`.
- Invulnerability flicker length — `Tuning.Player.invulnDuration` /
  `invulnBlinkHz`.
