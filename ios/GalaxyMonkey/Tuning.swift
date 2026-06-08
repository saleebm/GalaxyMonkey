//
//  Tuning.swift
//  GalaxyMonkey
//
//  Grouped tuning knobs. Adjust feel here; subsystems read these constants
//  rather than embedding numbers in code paths.
//

import CoreGraphics
import Foundation

enum Tuning {

    enum Player {
        static let maxSpeed: CGFloat = 360         // pt/s at full stick
        static let acceleration: CGFloat = 1600    // pt/s² toward target velocity
        static let drag: CGFloat = 2.4             // exponential damping when no input
        static let radius: CGFloat = 22            // collision radius
        static let startingLives: Int = 3
        // Hard cap on lives, including life-restore pickups. Matches the
        // HUD's pre-allocated banana-heart icon slots (HUDController).
        static let maxLives: Int = 5
        static let invulnDuration: TimeInterval = 1.6
        static let invulnBlinkHz: Double = 12
        // Max bank angle in radians the ship tilts to when sliding sideways
        // at full horizontal stick. 30° reads as a real bank without the
        // sprite looking like it's spinning 2D around its center.
        static let maxBankRadians: CGFloat = .pi / 6
        // Exponential approach rate (1/s) toward target bank angle. Higher =
        // snappier. ~8 gives a 125 ms time constant — the ship visibly leans
        // into a slide but doesn't snap.
        static let bankApproachRate: CGFloat = 8.0
    }

    enum Projectile {
        static let speed: CGFloat = 720
        static let fireInterval: TimeInterval = 0.15
        static let lifetime: TimeInterval = 1.4
        static let radius: CGFloat = 6
        static let poolSize: Int = 96
        // Per-side angular offset for the golden-banana spread shot.
        // Level 1 fires at ±offset; level 2 fires at 0 and ±2·offset.
        static let spreadOffset: CGFloat = .pi / 24   // 7.5°
        // Gorilla "poison banana" projectile — slow, telegraphed, giant, and
        // spinning. Internally still called "bomb" (same fire path / Sprite.bomb).
        static let bombSpeed: CGFloat = 280
        static let bombLifetime: TimeInterval = 3.0
        static let bombRadius: CGFloat = 26
        // Seconds per full spin of the banana while it flies.
        static let bombSpinPeriod: TimeInterval = 0.6
        // Enemy bullets — slowed and visually enlarged so the player can
        // read incoming shots. Physics radius stays the pool default; only
        // the rendered sprite scales up.
        static let enemyBulletSpeedMul: CGFloat = 0.45
        static let enemyBulletVisualRadius: CGFloat = 11
    }

    enum Enemy {
        static let spawnIntervalStart: TimeInterval = 2.7
        static let spawnIntervalEnd: TimeInterval = 0.75
        static let rampDuration: TimeInterval = 90
        static let spawnPaddingPx: CGFloat = 60     // beyond the visible bounds
        static let baseSpeed: CGFloat = 80
        static let speedJitter: CGFloat = 36        // ± random
        static let radius: CGFloat = 22
        // Spawn one gorilla boss for every N regular kills. The counter
        // resets on boss spawn — so killing the gorilla doesn't roll into
        // back-to-back boss waves.
        static let bossEveryNKills: Int = 25
        // Shooters only fire when the player is closer than this distance.
        // Keeps off-screen enemies quiet and biases attack pressure to the
        // visible play area.
        static let fireRangePx: CGFloat = 360
        // Default score per kill. Per-type overrides live on EnemyType.
        static let pointsOnKill: Int = 100
        // Minimum per-frame velocity (pt/s) for the visual to play the walk
        // loop. Below this the enemy switches to the idle loop. Enemies move
        // at ~80·speedMul pt/s baseline, so the threshold sits well below
        // even the slowest archetype (gorilla at 40 pt/s).
        static let walkSpeedThresholdPx: CGFloat = 12

        // Min horizontal distance-to-player (world px) before an enemy flips
        // its L/R facing. Deadzone prevents flicker on near-vertical approaches.
        static let facingFlipDeadzonePx: CGFloat = 8
    }

    enum Joystick {
        static let radius: CGFloat = 78
        static let thumbRadius: CGFloat = 34
        static let deadZone: CGFloat = 0.12         // fraction of radius
        static let edgeMargin: CGFloat = 32         // off-screen edge padding for resting positions
        // Visible-but-barely alphas. The sticks are hidden entirely when no
        // finger is down; these only apply while the stick is engaged.
        static let baseAlpha: CGFloat = 0.08
        static let strokeAlpha: CGFloat = 0.18
        static let thumbAlpha: CGFloat = 0.14
    }

    enum Starfield {
        static let layer1Count: Int = 80
        static let layer2Count: Int = 30
        static let layer1Speed: CGFloat = 0.06      // parallax factor
        static let layer2Speed: CGFloat = 0.18
        // Twinkle pass — a small set of bright screen-locked dots that
        // pulse asynchronously over the baked starfield. Sub-pixel bound,
        // no parallax (these are camera-fixed "near" stars).
        static let twinkleCount: Int = 16
        static let twinkleRadius: CGFloat = 1.6
        static let twinkleAlphaLow: CGFloat = 0.3
        static let twinklePeriodMin: TimeInterval = 1.2
        static let twinklePeriodMax: TimeInterval = 3.5
    }

    enum Audio {
        // Distance at which spatial SFX hit the minimum volume floor.
        static let maxDistance: CGFloat = 900
        // Volume floor so distant sounds don't fully vanish.
        static let minVolume: Float = 0.05
        // Banana shot is intentionally near-silent (~95% reduction).
        static let playerShotVolume: Float = 0.05
    }

    /// First-launch defaults for user-tunable settings. The live values are
    /// read/written through SettingsStore (UserDefaults-backed); these are
    /// only consulted when no persisted value exists yet.
    enum Settings {
        static let defaultMusicVolume: Float = 0.18
        static let defaultSFXVolume: Float = 1.0
        static let defaultHapticsEnabled: Bool = true
    }

    enum Pickup {
        // Drop probability per enemy kill (rolled in PickupSystem.trySpawnGoldenBanana).
        static let dropChance: Double = 0.08            // ~1 in 12 kills
        static let lifetime: TimeInterval = 8.0
        static let radius: CGFloat = 14
        static let spriteTargetMax: CGFloat = 36
        static let bobAmplitude: CGFloat = 4
        static let bobPeriod: TimeInterval = 1.2
        static let scoreBonus: Int = 500
        // Player.spreadLevel is clamped to this. 0 = single, 1 = 2-line, 2 = 3-line.
        static let maxSpreadLevel: Int = 2
    }

    enum VFX {
        // Screen shake — per-frame decay, max offset, and per-event intensities.
        static let shakeDecay: CGFloat = 0.86
        static let shakeMaxOffset: CGFloat = 18
        // Soft thud for the poison-banana splash (was 14 for the old bomb blast).
        static let bombShakeIntensity: CGFloat = 6
        static let playerHitShakeIntensity: CGFloat = 8
        static let enemyKillShakeIntensity: CGFloat = 3

        // Engine thrust plume (particle emitter — see ThrusterEmitter / Player).
        // Z just below the player's body so the plume reads as exhaust.
        static let thrustZ: CGFloat = -1
        // Plume tail offset behind the ship's local +X axis.
        static let thrustTailOffsetX: CGFloat = -22
        // Scale relative to ship radius.
        static let thrustScale: CGFloat = 1.4

        // Muzzle flash.
        static let muzzleFlashDuration: TimeInterval = 0.08
        static let muzzleFlashScale: CGFloat = 0.6

        // Damage flash (white tint on the ship body).
        static let damageFlashDuration: TimeInterval = 0.12

        // Bomb explosion (the 25-frame chunky sequence).
        static let bombExplosionFrameDuration: TimeInterval = 1.0 / 30
        // Visual size = Enemy.radius × this.
        static let bombExplosionScale: CGFloat = 3.4

        // Glow halo composited under the bomb detonation.
        static let glowBombDuration: TimeInterval = 0.35
        static let glowBombScale: CGFloat = 2.0

        // Blackhole warp — the kid-friendly enemy death. The sprite flushes
        // violet, then gently shrinks and fades into a soft violet halo that
        // glows in and out. Deliberately subtle: no spiral, no big spin.
        static let warpVioletDuration: TimeInterval = 0.12
        static let warpFadeDuration: TimeInterval = 0.3
        static let blackholeOpenDuration: TimeInterval = 0.15
        static let blackholeCollapseDuration: TimeInterval = 0.2
        // Halo display size = Enemy.radius × this.
        static let blackholeScale: CGFloat = 3.0
        // Seconds per full rotation of the soft halo.
        static let blackholeSpinPeriod: TimeInterval = 1.0

        // Slow self-rotation period in seconds (per planet, on its own axis).
        static let planetRotationPeriodMin: TimeInterval = 60
        static let planetRotationPeriodMax: TimeInterval = 180
    }

    /// Solar-system layout. The Sun sits at `worldCenter` (computed from scene
    /// size in GameScene). Planet rings are scaled into a roomy universe
    /// roughly 25× the scene width across; past Neptune is empty space.
    enum World {
        static let orbitTiltY: CGFloat = 0.45                // semiMinor/semiMajor → tilted ecliptic
        static let sunDisplayDiameter: CGFloat = 600
        // Per-planet display diameter (points).
        static let mercuryDiameter: CGFloat = 110
        static let venusDiameter:   CGFloat = 150
        static let earthDiameter:   CGFloat = 165
        static let marsDiameter:    CGFloat = 130
        static let jupiterDiameter: CGFloat = 360
        static let saturnDiameter:  CGFloat = 320
        static let uranusDiameter:  CGFloat = 230
        static let neptuneDiameter: CGFloat = 220
        // Orbital radii in points around the Sun.
        // Ratios follow real AU distances (Mercury 0.39, Venus 0.72,
        // Earth 1.00, Mars 1.52, Jupiter 5.20, Saturn 9.58, Uranus 19.2,
        // Neptune 30.05) with mild compression past Jupiter so Neptune
        // still fits inside the playable universe. The Mars→Jupiter jump
        // preserves the asteroid-belt gap.
        static let orbitMercury: CGFloat = 600
        static let orbitVenus:   CGFloat = 1100
        static let orbitEarth:   CGFloat = 1500
        static let orbitMars:    CGFloat = 2300
        static let orbitJupiter: CGFloat = 4000
        static let orbitSaturn:  CGFloat = 5400
        static let orbitUranus:  CGFloat = 7000
        static let orbitNeptune: CGFloat = 8500
        // Orrery orbit rings — faint elliptical strokes tracing each
        // planet's track around the Sun. Vector strokes (SKShapeNode) so
        // there's no texture rectangle to leak at large scale.
        static let orbitRingAlpha: CGFloat = 0.085
        static let orbitRingLineWidth: CGFloat = 1.5
        // Sun corona — a copy of the sun sprite wrapped in an SKEffectNode
        // with a Gaussian blur so the rasterized alpha falls off
        // continuously past the disk. Subtle scale pulse so the star
        // breathes. This is the "proper" halo path; the earlier raw-sprite
        // additive approach was rejected because the texture rectangle
        // showed at large scale.
        static let sunCoronaBlurRadius: CGFloat = 28
        static let sunCoronaAlpha: CGFloat = 0.55
        static let sunCoronaPulsePeriod: TimeInterval = 6.0
        static let sunCoronaPulseAmplitude: CGFloat = 0.04   // ±4% scale
        // Angular speed (rad/s) per planet. Ratios are Kepler-ish (inner
        // planets faster) but compressed so even Neptune visibly orbits
        // in a play session. All planets start at phase 0 so the game
        // opens on a clean conjunction line that drifts apart naturally.
        static let angSpeedMercury: CGFloat = 0.045
        static let angSpeedVenus:   CGFloat = 0.030
        static let angSpeedEarth:   CGFloat = 0.022
        static let angSpeedMars:    CGFloat = 0.015
        static let angSpeedJupiter: CGFloat = 0.009
        static let angSpeedSaturn:  CGFloat = 0.006
        static let angSpeedUranus:  CGFloat = 0.004
        static let angSpeedNeptune: CGFloat = 0.003
        // Comets — random spawn that streak across the world on a straight
        // chord with a particle trail. Adds non-orbital motion to the
        // cosmos. Particles detach into world space so the trail is left
        // behind in the comet's wake (not dragged along with the head).
        static let cometSpawnIntervalMin: TimeInterval = 22
        static let cometSpawnIntervalMax: TimeInterval = 48
        // Path radius is chosen so chords pass through the inner-to-middle
        // solar system where the player typically is (Earth at 1500,
        // asteroid belt at ~3200, Jupiter at 4000). Far enough out to feel
        // like an interloper; close enough that the player actually sees
        // one occasionally.
        static let cometPathRadius: CGFloat = 4200
        // Max angular deviation from a 180° (origin-crossing) chord. With
        // ±15° jitter the closest approach to origin is r·sin(7.5°) ≈
        // 0.13·r ≈ 550pt, so comets pass through the inner planets area.
        static let cometExitJitterRad: CGFloat = .pi / 12
        static let cometHeadRadius: CGFloat = 3
        static let cometTravelDurationMin: TimeInterval = 7
        static let cometTravelDurationMax: TimeInterval = 12
        static let cometTrailBirthRate: CGFloat = 90
        static let cometTrailLifetime: TimeInterval = 1.4
        static let cometInitialDelay: TimeInterval = 3   // first comet within seconds of game start
        // Moons — Luna (Earth), Galileans (Jupiter ×4), Titan (Saturn).
        // Diameter is the rendered point size; radius is the orbital radius
        // around the parent planet's center in points; angular speed in
        // rad/s. Inner moons orbit faster than outer ones (Kepler-ish).
        enum Moons {
            // Earth's Luna.
            static let lunaDiameter: CGFloat = 38
            static let lunaRadius:   CGFloat = 130
            static let lunaSpeed:    CGFloat = 0.08
            // Jupiter's Galileans, inside → out.
            static let ioDiameter:       CGFloat = 32
            static let ioRadius:         CGFloat = 240
            static let ioSpeed:          CGFloat = 0.14
            static let europaDiameter:   CGFloat = 28
            static let europaRadius:     CGFloat = 295
            static let europaSpeed:      CGFloat = 0.10
            static let ganymedeDiameter: CGFloat = 40
            static let ganymedeRadius:   CGFloat = 355
            static let ganymedeSpeed:    CGFloat = 0.075
            static let callistoDiameter: CGFloat = 36
            static let callistoRadius:   CGFloat = 425
            static let callistoSpeed:    CGFloat = 0.055
            // Saturn's Titan.
            static let titanDiameter: CGFloat = 42
            static let titanRadius:   CGFloat = 320
            static let titanSpeed:    CGFloat = 0.065
        }
        // Asteroid belt between Mars and Jupiter. The mean angular speed
        // sits between Mars and Jupiter; each rock gets a small ±jitter
        // so the belt churns rather than rotates as a rigid disk.
        static let asteroidBeltInnerRadius: CGFloat = 2750
        static let asteroidBeltOuterRadius: CGFloat = 3700
        static let asteroidBeltCount: Int = 180
        static let asteroidBeltAngularSpeed: CGFloat = 0.012
        static let asteroidBeltSpeedJitter: CGFloat = 0.0035   // ±rad/s
        static let asteroidDiameterMin: CGFloat = 5
        static let asteroidDiameterMax: CGFloat = 18
        static let asteroidAlphaMin: CGFloat = 0.45
        static let asteroidAlphaMax: CGFloat = 0.9
    }

    enum Camera {
        /// Player can drift this many points off-center before the camera reacts.
        static let deadzoneRadius: CGFloat = 36
        /// Per-second lerp factor toward the target. Larger = snappier follow.
        static let followLerpPerSec: CGFloat = 6
        /// Extra padding beyond the camera viewport for enemy spawn placement.
        static let enemySpawnViewPaddingPx: CGFloat = 96
    }
}

/// Physics category bitmasks for SKPhysicsContact wiring.
struct Category {
    static let none:        UInt32 = 0
    static let player:      UInt32 = 1 << 0
    static let enemy:       UInt32 = 1 << 1
    /// Player-fired projectiles only. Enemy projectiles use `enemyBullet`
    /// so friendly fire doesn't damage other enemies in flight path.
    static let bullet:      UInt32 = 1 << 2
    static let pickup:      UInt32 = 1 << 3
    static let enemyBullet: UInt32 = 1 << 4
}
