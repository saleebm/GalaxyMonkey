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

## Device verify: IOGPUMetalError on backgrounding

### Why

Backgrounding the app used to surface `IOGPUMetalError: Insufficient
Permission` in the device console as Metal kept rendering after the
app lost focus. The pause-on-focus-loss path in
`GameScene.applicationDidLoseFocus()` is the fix; this section is
the manual gate that confirms the regression stays fixed on hardware.

### Prereqs

- iOS 17+ device paired and trusted to the host Mac.
- Console.app open with the device selected. Filter:
  process `GalaxyMonkey` AND text `IOGPU OR Metal OR MTL`.
- Launch via `./run-device.sh`.

### Repro A — background from start prompt

1. Launch the app.
2. Confirm "Tap to start" is visible.
3. Swipe up to the home screen (do not tap start first).
4. Wait 10 seconds.
5. Foreground the app from the app switcher.

**Pass:** no `IOGPUMetalError` lines in the console after the
foreground event. App returns to the start prompt.

### Repro B — background mid-run

1. Launch, tap to start, play for ~5 seconds.
2. Swipe up to the home screen.
3. Wait 10 seconds.
4. Foreground.

**Pass:** pause menu is visible on return; no `IOGPUMetalError` in
the console. (Pause-on-focus-loss is intentional — the player
resumes via the Resume label.)

### Repro C — background on game-over overlay

1. Launch, tap to start.
2. In a DEBUG build, tap the small red `debugForceGameOver` label
   in the top-left corner to force the game-over screen.
3. Swipe up to the home screen.
4. Wait 10 seconds.
5. Foreground.

**Pass:** the game-over overlay is still visible; no
`IOGPUMetalError` in the console.

### If it reproduces

- Capture a sysdiagnose
  (Settings → Privacy & Security → Analytics & Improvements).
- Note iOS version, device model, and the console line range
  around the foreground event.
- File a follow-up issue or bead with the captured logs attached.
