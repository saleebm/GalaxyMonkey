# HANDOFF — Finish the Android LibGDX port bead plan (recovery)

## Situation
We are planning the Android (Kotlin + LibGDX, Android-only) port of the iOS SpriteKit game **Galaxy Monkey** as a Beads graph, to be implemented later by an NTM swarm. A multi-agent Workflow created the epic + ~49 child beads, then **failed**: the per-track create agents wrote every description to `/tmp/*.txt` then issued many `br create` calls each, going silent long enough to trip the Workflow's 180s "no-progress" detector (and likely a SQLite write-lock wait).

**Your job:** finish the bead plan — create the MISSING beads and wire ALL cross-track dependencies — using ONLY the `br` CLI. You are the single writer; no other process will touch `.beads` while you run.

> ⚠️ NOTE: This repo's `.beads` was forked from a different "PenguinSlide" project, so the prefix is `penguinslide-` and there are ~19 pre-existing PENGUIN-game beads (e.g. `penguinslide-yla`, `penguinslide-y7f`, `penguinslide-ga8`, `penguinslide-9f8`, and any titled with Penguin/Icicle/landing/near-miss). **Per the project owner: LEAVE those penguin beads completely untouched** — do NOT close, edit, delete, or add deps to them. The `penguinslide-` prefix stays as-is (cosmetic). Only work with the Galaxy Monkey port epic `penguinslide-f5a` and its children.

> 🤝 AGENT MAIL: Before creating beads, register with MCP Agent Mail via `macro_start_session(human_key="/Users/minasaleeb/workspaces/GalaxyMonkey", program="claude-code", model="<your model>", task_description="Finish Android port bead plan")`, then reserve the beads DB: `file_reservation_paths(paths=[".beads/**"], ttl_seconds=3600, exclusive=true, reason="finish-android-port-beads")`. When done, release the reservation and send a completion summary to agent `PurpleGorge`.

## Hard rules (avoid the previous stall)
- Use ONLY `br` to create/modify beads + deps. Do not edit source code.
- Pass descriptions INLINE with `-d "..."` (escape quotes). Do NOT write temp files for descriptions. Keep each `br create` self-contained and quick.
- If you hit a lock, add `--lock-timeout 30000`. Emit a short `echo` of progress every few creates so you never go silent for >2 min.
- Make creation IDEMPOTENT: before creating, `br list --json | jq` to confirm a bead with that title doesn't already exist (the 49 below already exist — do NOT duplicate them).
- Every feature/task bead must hang under the epic. Existing ones used the native parent flag (IDs like `penguinslide-f5a.NN`). Use the same `br create` parent mechanism (check `br create --help`; existing beads prove it works). Epic id = **penguinslide-f5a**.
- Each non-trivial feature bead MUST have a paired TEST bead that depends on it (unit for logic, e2e/smoke for UI/integration), with detailed logging expectations.
- Descriptions must be self-contained: background + reasoning + exact SpriteKit→LibGDX mapping + the iOS file/values ported + crisp acceptance criteria. Detailed enough that an implementer never needs any other doc.

## Project facts (for descriptions)
- iOS source: `/Users/minasaleeb/workspaces/GalaxyMonkey/GalaxyMonkey/*.swift` (read the relevant file before writing a bead).
- Feel constants are authoritative in `Tuning.swift` (deadZone=0.12, fireInterval=0.15, projectile speed=720, bomb speed=280, spread offset=π/24, spawn ramp 2.7→0.75s over 90s, bossEveryNKills=25, player drag=2.4, maxSpeed=360, accel=1600, invuln=1.6, pickup dropChance=0.08/lifetime 8s, camera deadzoneRadius=36/followLerp 6, shakeDecay=0.86/maxOffset=18).
- Target layout: monorepo — iOS moves to `ios/`; port under `android/` as `core/` (Kotlin logic+render) + `app/` (Android module, manifest, assets/).
- Collision is a LIGHTWEIGHT custom circle-overlap layer (NOT Box2D).
- Menus: explicit buttons + generous pill tap targets; NEVER dismiss by tapping empty space.

## EXISTING beads (DO NOT recreate) — 49 under epic penguinslide-f5a
Track0: .1 move iOS, .2 verify-iOS-builds GATE, .4 gradle skeleton, .5 core module, .6 app module+manifest, .7 build smoke.
Track1: .10 extract/flatten PNGs, .11 test extraction, .12 pack game.atlas, .13 test atlas regions, .14 pack 33 anim atlases, .15 test anim ordering.
Track2: .23 Tuning.kt+Category, .24 test Tuning parity.
Track3: .29 GalaxyMonkeyGame, .31 GameScreen scaffold, .33 fixed-timestep loop, .35 pause/resume+gameplayPaused, .36 test pause-freeze, .41 circle-overlap collision, .42 test collision, .43 contact dispatch, .44 test dispatch.
Track5: .58 Player.kt, .59 test Player, .60 EnemyType.kt, .62 ProjectileSystem.kt, .64 Enemy entity model.
Track6: .73 bake halo texture, .78 test thruster, .79 Starfield.kt, .80 test starfield.
Track7: .89 SettingsStore, .90 test settings, .91 HUDController scaffold, .92 test HUD, .93 score/best labels, .94 test labels, .95 lives hearts, .96 test lives, .97 pause button, .98 test pause btn, .99 start prompt, .101 game-over overlay, .103 menu hit-test helper, .104 test hit-test, .105 pause menu, .106 test pause menu, .107 settings layout, .109 settings sliders.

## MISSING beads to CREATE (with a paired test where noted)
Track1 (assets/audio): convert 8 `.caf`→`.ogg` via ffmpeg into app assets/sounds/ (+test: all 8 present & decodable); `SpriteCatalog.kt` (atlas region lookup, graceful shape fallback) (+test); `AnimationCatalog.kt` (frame-sequence lookup, loop/oneshot, fallback) (+test).
Track2 (core): generic object `Pool<T>` (mirror ProjectileSystem/VFX pooling) (+test); Vector2 math/util helpers (angle, clamp, lerp) (+test).
Track3 (engine): camera follow with dead-zone (deadzoneRadius=36, followLerp=6) (+test); screen-shake with exponential decay + per-event intensity (shakeDecay=0.86, maxOffset=18) (+test).
Track4 (input — ALL MISSING): `VirtualJoystick.kt` dynamic dual sticks, dynamic centering, 12% dead-zone, magnitude remap, screen-space rings, multitouch (+test: deadzone math, remap, dynamic-center); `GameControllerInput.kt` Android controller, same deadzone, precedence over touch (+test); input multiplexer wiring (InputProcessor/InputMultiplexer) (+test).
Track5 (entities): `EnemySystem.kt` (spawn ramp + off-screen edge spawn, homing AI, L/R facing flips, attack behaviors melee/shoot/bombThrow, idle/walk anim states, boss every 25 kills, HitOutcome) (+test: spawn ramp, boss cadence, facing flip, hit outcomes); `PickupSystem.kt` (golden banana 8% drop, sine bob, 8s lifetime, collect) (+test); test for `EnemyType.kt` (.60); test for `ProjectileSystem.kt` (.62: fire cooldown, spread 0/1/2 angles, bomb).
Track6 (rendering): `VFXPool.kt` effects — muzzle flash, damage flash, blackhole warp, glow halos (uses .73 halo) (+test); `ThrusterEmitter.kt` feature — particle color ramp white→orange→red→smoke, birthrate by stick magnitude (.78 test already exists, link it); `PlanetField.kt` solar orrery — sun+8 planets+moons+asteroid belt+comets, terminator GLSL→ShaderProgram, WITH static-shading fallback (riskiest) (+test: orbit math, z-order swap, comet spawn cadence).
Track7 (menus): test for settings sliders (.109); haptics toggle control in settings menu (+test); settings live-wiring to AudioController/SettingsStore (+test).
Track8 (audio/haptics — ALL MISSING): `AudioController.kt` (Music BGM loop, Sound SFX, spatial distance attenuation to maxDistance w/ minVolume floor, pause-ducking 50%, live volume; player shot quiet) (+test); `HapticsController.kt` (Android Vibrator, gated live by settings) (+test).
Track9 (integration/QA — ALL MISSING): integration bead — wire all systems in GameScreen (input→update→collision→camera→parallax→shake→render) + start/gameover/pause state machine; JUnit unit-test-suite bead (Tuning parity, drag, spread, spawn ramp, deadzone, boss cadence); e2e/smoke bead (headless gdx gameplay loop + emulator launch, detailed logging); feel-parity checklist bead vs iOS; APK install/run bead (`./gradlew :app:installDebug`).

## Dependency spine to WIRE (`br dep add <child> <parent>` = child depends on parent)
- Scaffold order: .5←.4, .6←.5, .7←.6; .2←.1. Treat `.5` (core module) as the prerequisite for ALL Kotlin code beads; `.6` for anything needing the app module/assets.
- Track1: .12←.10, .13←.12, .14←.10, .15←.14; catalogs←.12/.14; audio-convert←(none code) but catalog-of-audio/AudioController←audio-convert.
- Track2: .23←.5, .24←.23, Pool←.5, math←.5.
- Track3: .29←.5, .31←.29 & .31←.23, .33←.31, .35←.31, .41←.5, .43←.41, camera←.31, shake←.31.
- Track4: joystick←.5 & ←.23, controller←.5, multiplexer←joystick & controller.
- Track5: .60←.23, .64←.60, .58←.23 & .31 & .43 & joystick, .62←.23 & Pool & .43, EnemySystem←.64 & .31 & .43 & .62, Pickups←.23 & .43. Tests←their feature.
- Track6: VFX←.73 & .31, Thruster←.31 & .58, .79←.31, PlanetField←.31. Tests←their feature.
- Track7: .89←.5, .91←.31, labels/lives/pausebtn←.91, start/gameover←.91, hit-test←.91, pause menu←hit-test, settings layout←hit-test & .89, sliders←settings layout, haptics-toggle←settings layout, settings-wiring←sliders & AudioController & HapticsController.
- Track8: AudioController←audio-convert & .23 & .5, Haptics←.5 & .89.
- Track9: integration←.58 & EnemySystem & .62 & Pickups & .91 & pause menu & VFX & .79 & AudioController; unit-suite←integration; e2e←integration; feel-parity←integration; APK←.7 & integration.
- Epic membership: ensure every bead is a child of penguinslide-f5a (existing ones already are; do the same for new ones).

## Finish criteria
1. `br dep cycles` → empty.
2. `bv --robot-insights` → note bottlenecks; the scaffold beads (.4/.5/.6/.7) should be the only initially-ready leaves of the spine.
3. Report: total beads, beads created this run, deps added, cycles status, and any unresolved gaps.
