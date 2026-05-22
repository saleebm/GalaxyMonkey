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
        // Gorilla bomb — slow, heavy, larger contact radius.
        static let bombSpeed: CGFloat = 280
        static let bombLifetime: TimeInterval = 3.0
        static let bombRadius: CGFloat = 16
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
    }

    enum Audio {
        // Distance at which spatial SFX hit the minimum volume floor.
        static let maxDistance: CGFloat = 900
        // Volume floor so distant sounds don't fully vanish.
        static let minVolume: Float = 0.05
        // Banana shot is intentionally near-silent (~95% reduction).
        static let playerShotVolume: Float = 0.05
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
        static let bombShakeIntensity: CGFloat = 14
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

        // Glow halo composited under explosions.
        static let glowEnemyKillDuration: TimeInterval = 0.18
        static let glowBombDuration: TimeInterval = 0.35
        static let glowEnemyKillScale: CGFloat = 1.0
        static let glowBombScale: CGFloat = 2.0

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
        // Angular speed (rad/s). Inner planets faster.
        static let angSpeedMercury: CGFloat = 0.090
        static let angSpeedVenus:   CGFloat = 0.060
        static let angSpeedEarth:   CGFloat = 0.045
        static let angSpeedMars:    CGFloat = 0.030
        static let angSpeedJupiter: CGFloat = 0.018
        static let angSpeedSaturn:  CGFloat = 0.012
        static let angSpeedUranus:  CGFloat = 0.008
        static let angSpeedNeptune: CGFloat = 0.005
        // Per-orbit inclination range (rad). Real solar-system planets
        // stay within ±3.5° of the ecliptic, so keep this small — the
        // orbitTiltY perspective squash provides the 3D feel.
        static let inclinationRange: CGFloat = 0.08
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
